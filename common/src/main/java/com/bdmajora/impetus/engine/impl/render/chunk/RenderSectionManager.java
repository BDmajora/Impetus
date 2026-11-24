package com.bdmajora.impetus.engine.impl.render.chunk;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import lombok.Getter;
import com.bdmajora.impetus.engine.impl.common.util.TimeUtil;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.gl.profiling.TimerQueryManager;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildContext;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkBuildOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkTaskOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.executor.ChunkBuilder;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.executor.ChunkJobMetricsTracker;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.executor.ChunkJobResult;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.executor.ChunkJobCollector;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks.ChunkBuilderSortTask;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.tasks.ChunkBuilderTask;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.chunk.data.MinecraftBuiltRenderSectionData;
import com.bdmajora.impetus.engine.impl.render.chunk.fog.FogService;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.ChunkRenderList;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.RenderListManager;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.SectionGraph;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.SectionTicker;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.SortedRenderLists;
import com.bdmajora.impetus.engine.impl.render.chunk.metrics.RenderSectionMetricsTracker;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.AsyncOcclusionMode;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegionManager;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshFog;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainConfig;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainRenderer;
import com.bdmajora.impetus.engine.impl.render.mesh.MeshTerrainShading;
import com.bdmajora.impetus.engine.impl.render.viewport.CameraTransform;
import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.render.viewport.frustum.ShadowSearchFrustum;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import com.bdmajora.impetus.engine.impl.util.iterator.ByteIterator;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.ChunkSortOutput;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.CutPlaneIndex;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.PartitionTree;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.SortState;
import com.bdmajora.impetus.engine.impl.util.suppliers.ExpiringSupplier;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;
import org.joml.*;
import org.lwjgl.opengl.GL11;

import java.lang.Math;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

public abstract class RenderSectionManager {
    // When true every section is marked for a remesh whenever the update queue empties.
    protected static final boolean CONTINUOUSLY_REMESH_WORLD = false;

    // Frames of dispatch the search may collect into the initial build list.
    private static final int REBUILD_LIST_FRAMES = 2;

    private final ChunkBuilder builder;

    private final Thread renderThread = Thread.currentThread();

    private final RenderRegionManager regions;

    private final Long2ReferenceMap<RenderSection> sectionByPosition = new Long2ReferenceOpenHashMap<>();

    private final ConcurrentLinkedDeque<ChunkJobResult<? extends ChunkTaskOutput>> buildResults = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Runnable> asyncSubmittedTasks = new ConcurrentLinkedDeque<>();

    private final ChunkRenderer chunkRenderer;

    private final int renderDistance;

    // Camera position rounded down to block coordinates.
    protected @Nullable Vector3ic cameraBlockPosition;

    protected Vector3d cameraPosition = new Vector3d();

    // Exact camera position of the last frame.
    protected @Nullable Vector3d previousCameraPosition;

    // Orders build results independently of when a visibility search finishes.
    private int submissionFrame;

    private final CutPlaneIndex<RenderSection> cutPlaneIndex = new CutPlaneIndex<>();

    private final ChunkJobMetricsTracker.MetricsData treeSortMetrics = new ChunkJobMetricsTracker.MetricsData();
    private long treeStatsWindowStart = System.nanoTime();
    private int treeTriggersThisSecond, treeTriggersLastSecond;

    @Getter
    private final RenderPassConfiguration<?> renderPassConfiguration;

    private final Set<TerrainRenderPass> disabledRenderPasses;

    private final int minSection, maxSection;

    // Lattice and search thread shared by the terrain and shadow passes.
    private final SectionGraph sectionGraph;

    protected final RenderListManager renderListManager;

    @Nullable
    protected final RenderListManager shadowRenderListManager;

    // Set by the shadow pass when it ran this frame.
    private boolean shadowPassRanThisFrame;

    // Frame number every search stamps.
    private int searchFrame;

    // Sink shared by every section that mirrors packed metadata changes into the list managers.
    private final RenderSection.MetadataSink metadataSink = this::pushSectionMetadata;

    protected final ReferenceSet<RenderSection> sectionsWithGlobalEntities = new ReferenceOpenHashSet<>();

    private final Object2ObjectOpenHashMap<TerrainRenderPass, TimerQueryManager> renderPassDrawTimers = new Object2ObjectOpenHashMap<>();

    protected final ReferenceSet<RenderSection> sectionsRequestingUpdate = new ReferenceOpenHashSet<>();

    @Getter
    protected final ChunkJobMetricsTracker jobMetricsTracker = new ChunkJobMetricsTracker();

    @Getter
    protected final RenderSectionMetricsTracker sectionMetricsTracker = new RenderSectionMetricsTracker();

    // Mesh shader backend or null.
    private final @Nullable MeshTerrainRenderer meshTerrain;

    // The frame's main pass viewport.
    private @Nullable Viewport frameViewport;
    // The shadow pass frustum.
    private @Nullable Viewport shadowViewport;

    public RenderSectionManager(RenderPassConfiguration<?> configuration, Supplier<ChunkBuildContext> contextSupplier,
                                BiFunction<RenderDevice, RenderPassConfiguration<?>, ChunkRenderer> chunkRenderer,
                                int renderDistance, CommandList commandList, int minSection, int maxSection,
                                int requestedThreads, boolean hasShadowPass) {
        this(configuration, contextSupplier, chunkRenderer, renderDistance, commandList, minSection, maxSection,
                requestedThreads, hasShadowPass, null);
    }

    public RenderSectionManager(RenderPassConfiguration<?> configuration, Supplier<ChunkBuildContext> contextSupplier,
                                BiFunction<RenderDevice, RenderPassConfiguration<?>, ChunkRenderer> chunkRenderer,
                                int renderDistance, CommandList commandList, int minSection, int maxSection,
                                int requestedThreads, boolean hasShadowPass, @Nullable MeshTerrainConfig meshTerrain) {
        this(configuration, contextSupplier, chunkRenderer, renderDistance, commandList, minSection, maxSection,
                requestedThreads, hasShadowPass, meshTerrain, null);
    }

    // Creates the manager and the mesh backend when a mesh config is given.
    public RenderSectionManager(RenderPassConfiguration<?> configuration, Supplier<ChunkBuildContext> contextSupplier,
                                BiFunction<RenderDevice, RenderPassConfiguration<?>, ChunkRenderer> chunkRenderer,
                                int renderDistance, CommandList commandList, int minSection, int maxSection,
                                int requestedThreads, boolean hasShadowPass, @Nullable MeshTerrainConfig meshTerrain,
                                @Nullable MeshTerrainShading meshShading) {
        this.meshTerrain = meshTerrain != null ? new MeshTerrainRenderer(meshTerrain, (maxSection - minSection + 3) / 4, meshShading) : null;
        this.chunkRenderer = chunkRenderer.apply(RenderDevice.INSTANCE, configuration);

        this.renderPassConfiguration = configuration;

        this.builder = new ChunkBuilder(this::managedBlock, contextSupplier, requestedThreads);

        this.renderDistance = renderDistance;

        this.regions = new RenderRegionManager(commandList, this.renderPassConfiguration);

        this.minSection = minSection;
        this.maxSection = maxSection;
        AsyncOcclusionMode asyncMode = this.getAsyncOcclusionMode();
        this.sectionGraph = new SectionGraph(this.minSection, this.maxSection, asyncMode, hasShadowPass, this.useRasterOcclusionCulling());
        this.renderListManager = new RenderListManager(this.sectionGraph, false, asyncMode, this.createSectionTicker());
        if (hasShadowPass) {
            this.shadowRenderListManager = new RenderListManager(this.sectionGraph, true, asyncMode, this.createSectionTicker());
        } else {
            this.shadowRenderListManager = null;
        }

        this.disabledRenderPasses = new ReferenceArraySet<>();
    }

    protected abstract AsyncOcclusionMode getAsyncOcclusionMode();

    protected @Nullable SectionTicker createSectionTicker() {
        return null;
    }

    public void managedBlock(BooleanSupplier isDone) {
        while (!isDone.getAsBoolean()) {
            Runnable task = this.asyncSubmittedTasks.poll();
            if (task != null) {
                task.run();
            } else {
                LockSupport.parkNanos("Wait", 100000L);
            }
        }
    }

    public void runAsyncTasks() {
        Runnable task;

        while ((task = this.asyncSubmittedTasks.poll()) != null) {
            task.run();
        }

        this.renderPassDrawTimers.values().forEach(TimerQueryManager::updateTime);
    }

    // Returns whether terrain is being drawn for shadows.
    public boolean isInShadowPass() {
        return false;
    }

    protected boolean isDebugInfoShown() {
        return false;
    }

    // Runs the terrain search unless the shadow pass already did and updates the camera.
    public void update(Viewport positionedViewport, int frame, boolean spectator) {
        this.updateTranslucency(positionedViewport);

        if (this.shadowPassRanThisFrame) {
            // Clears the shadow pass flag.
            this.shadowPassRanThisFrame = false;
        } else {
            this.createTerrainRenderList(positionedViewport, null, ++this.searchFrame, spectator);
        }
    }

    // Tracks sort movement even in frames that did not need a visibility search.
    public void updateTranslucency(Viewport playerViewport) {
        if (!this.shadowPassRanThisFrame) {
            this.updateCameraPosition(playerViewport);
        }
        this.checkTranslucencyChange();
    }

    // Runs a due terrain search for the player viewport and then the shadow search.
    public void updateForShadowPass(Viewport playerViewport, Viewport shadowViewport, int frame, boolean spectator) {
        if (this.shadowRenderListManager == null) {
            throw new IllegalStateException("No shadow pass configured");
        }

        this.updateCameraPosition(playerViewport);
        this.shadowPassRanThisFrame = true;
        this.shadowViewport = shadowViewport;

        // Stamps both searches with one frame number.
        int searchFrame = ++this.searchFrame;

        if (this.renderListManager.isNeedsUpdate()) {
            this.createTerrainRenderList(playerViewport, null, searchFrame, spectator);
        }

        Vector3fc lightVector = null;

        if (shadowViewport.getFrustum() instanceof ShadowSearchFrustum searchFrustum && searchFrustum.supportsOcclusionSearch()) {
            lightVector = new Vector3f(searchFrustum.shadowLightX(), searchFrustum.shadowLightY(), searchFrustum.shadowLightZ());
        }

        this.shadowRenderListManager.startShadowGraphUpdate(shadowViewport, searchFrame, this.regions.getRegionIdsLength(),
                this.getSearchDistance(null), lightVector, this.getTargetQueueSize());
    }

    private void updateCameraPosition(Viewport positionedViewport) {
        if (this.cameraBlockPosition != null) {
            this.previousCameraPosition = this.cameraPosition;
        }

        this.cameraBlockPosition = positionedViewport.getBlockCoord();
        var transform = positionedViewport.getTransform();
        this.cameraPosition = new Vector3d(transform.x, transform.y, transform.z);
    }

    public boolean hasShadowPass() {
        return this.shadowRenderListManager != null;
    }

    // Returns whether the shadow pass already ran this frame.
    public boolean didShadowPassRunThisFrame() {
        return this.shadowPassRanThisFrame;
    }

    private void checkTranslucencyChange() {
        long now = System.nanoTime();
        if (now - this.treeStatsWindowStart >= ChunkJobMetricsTracker.OBSERVATION_COUNT_TIME) {
            this.treeTriggersLastSecond = this.treeTriggersThisSecond;
            this.treeTriggersThisSecond = 0;
            this.treeSortMetrics.flipInterval();
            this.treeStatsWindowStart = now;
        }

        if (cameraBlockPosition == null || !this.renderPassConfiguration.defaultTranslucentMaterial().pass.isSorted()) {
            return;
        }

        this.scheduleTranslucencyUpdates();
        this.scheduleTreeSortedUpdates();
    }

    private void scheduleTranslucencyUpdates() {
        var renderListManager = this.getCurrentRenderListManager();
        var translucentPass = this.renderPassConfiguration.defaultTranslucentMaterial().pass;
        if (this.meshTerrain == null && !this.hasTranslucencySortedSections()) {
            return;
        }
        for (Iterator<ChunkRenderList> it = renderListManager.getRenderLists().iterator(); it.hasNext(); ) {
            ChunkRenderList entry = it.next();
            var region = entry.getRegion();
            if (this.meshTerrain == null && !region.hasSectionsInPass(translucentPass)) {
                continue;
            }
            ByteIterator sectionIterator = entry.sectionsNeedingDynamicSortIterator();
            if (sectionIterator == null) {
                continue;
            }
            while (sectionIterator.hasNext()) {
                var section = region.getSection(sectionIterator.nextByteAsInt());

                // tree-sorted sections are retriggered by scheduleTreeSortedUpdates() instead
                if (section == null || section.getSortMode() != RenderSection.SortMode.DYNAMIC) {
                    continue;
                }

                double dx = cameraPosition.x - section.lastCameraX;
                double dy = cameraPosition.y - section.lastCameraY;
                double dz = cameraPosition.z - section.lastCameraZ;
                if (dx * dx + dy * dy + dz * dz >= 1) {
                    this.scheduleSort(section);
                }
            }
        }
    }

    // Resorts tree sorted sections whose cut planes this frame's movement crossed.
    private void scheduleTreeSortedUpdates() {
        if (this.previousCameraPosition == null || this.cutPlaneIndex.size() == 0) {
            return;
        }

        this.cutPlaneIndex.query(
                this.previousCameraPosition.x, this.previousCameraPosition.y, this.previousCameraPosition.z,
                this.cameraPosition.x, this.cameraPosition.y, this.cameraPosition.z,
                section -> {
                    this.treeTriggersThisSecond++;
                    this.scheduleSort(section);
                }
        );
    }

    private void scheduleSort(RenderSection section) {
        ChunkUpdateType update = ChunkUpdateType.getPromotionUpdateType(section.getPendingUpdate(),
                (allowImportantRebuilds() && this.shouldPrioritizeRebuild(section)) ? ChunkUpdateType.IMPORTANT_SORT : ChunkUpdateType.SORT);

        if (update == null) {
            // We wouldn't be able to resort this section anyway
            return;
        }

        section.setPendingUpdate(update);
        // A visibility search may replace the current rebuild lists before the next upload.
        this.sectionsRequestingUpdate.add(section);
    }

    // Returns whether per frame queue limits apply.
    protected boolean shouldRespectUpdateTaskQueueSizeLimit() {
        return true;
    }

    private void createTerrainRenderList(Viewport viewport, Matrix4fc projectionMatrix, int frame, boolean spectator) {
        final var searchDistance = this.getSearchDistance(projectionMatrix);
        final var useOcclusionCulling = this.shouldUseOcclusionCulling(viewport, spectator);

        this.renderListManager.startGraphUpdate(viewport, frame, this.regions.getRegionIdsLength(),
                searchDistance, useOcclusionCulling, this.getTargetQueueSize());
    }

    private int getTargetQueueSize() {
        if (this.shouldRespectUpdateTaskQueueSizeLimit()) {
            return (int) Math.min(Integer.MAX_VALUE, (long) this.builder.getTargetQueueSize() * REBUILD_LIST_FRAMES);
        } else {
            return Integer.MAX_VALUE;
        }
    }

    protected abstract boolean useFogOcclusion();

    protected abstract boolean useRasterOcclusionCulling();

    private float getSearchDistance(@Nullable Matrix4fc projectionMatrix) {
        float distance;

        if (this.useFogOcclusion()) {
            distance = this.getEffectiveRenderDistance(projectionMatrix);
        } else {
            distance = this.getRenderDistance();
        }

        return distance;
    }

    protected abstract boolean shouldUseOcclusionCulling(Viewport viewport, boolean spectator);

    public abstract FogService getFogService();

    // Returns whether a pass of the current render lists is sorted.
    private boolean hasTranslucencySortedSections() {
        for (TerrainRenderPass pass : this.getCurrentRenderListManager().getRenderLists().getPasses()) {
            if (pass.isSorted()) {
                return true;
            }
        }
        return false;
    }

    protected abstract boolean isSectionVisuallyEmpty(int x, int y, int z);

    public void onSectionAdded(int x, int y, int z) {
        long key = PositionUtil.packSection(x, y, z);

        if (this.sectionByPosition.containsKey(key)) {
            return;
        }

        RenderRegion region = this.regions.createForChunk(x, y, z);

        RenderSection renderSection = new RenderSection(region, x, y, z);
        region.addSection(renderSection);
        renderSection.setMetadataSink(this.metadataSink);

        this.sectionByPosition.put(key, renderSection);

        this.sectionGraph.attachRenderSection(renderSection);
        this.markGraphDirty();

        this.invalidateCachedSectionData(renderSection);

        if (this.isSectionVisuallyEmpty(x, y, z)) {
            this.updateSectionInfo(renderSection, RenderSection.EMPTY_DATA);
        } else {
            renderSection.setPendingUpdate(ChunkUpdateType.INITIAL_BUILD);
        }


        this.markGraphDirty();
    }

    public void onSectionRemoved(int x, int y, int z) {
        RenderSection section = this.sectionByPosition.remove(PositionUtil.packSection(x, y, z));

        if (section == null) {
            return;
        }

        RenderRegion region = section.getRegion();

        if (region != null) {
            region.removeSection(section);
        }

        this.invalidateCachedSectionData(section);

        this.updateSectionInfo(section, null);

        this.sectionGraph.detachRenderSection(section);
        this.markGraphDirty();

        this.sectionMetricsTracker.removeSection(section);

        this.cutPlaneIndex.remove(section);

        if (this.meshTerrain != null) {
            this.meshTerrain.remove(x, y, z);
        }

        section.delete();

        this.markGraphDirty();
    }

    public void renderLayer(ChunkRenderMatrices matrices, TerrainRenderPass pass, CameraTransform occlusionCamera, CameraTransform camera) {
        if (disabledRenderPasses.contains(pass)) {
            return;
        }

        if (this.meshTerrain != null) {
            this.renderMeshLayer(matrices, pass, camera);
            return;
        }

        RenderDevice device = RenderDevice.INSTANCE;
        CommandList commandList = device.createCommandList();

        // Profiles the main pass only.
        boolean shouldProfile = isDebugInfoShown() && !isInShadowPass();

        TimerQueryManager timer = null;

        if (shouldProfile) {
            timer = renderPassDrawTimers.computeIfAbsent(pass, $ -> new TimerQueryManager());
            timer.startProfiling();
        }

        this.chunkRenderer.render(matrices, commandList, this.getCurrentRenderListManager().getRenderLists(), pass, occlusionCamera, camera);

        if (shouldProfile) {
            timer.finishProfiling();
        }

        commandList.flush();
    }

    // Draws every resident mesh section for the pass.
    private void renderMeshLayer(ChunkRenderMatrices matrices, TerrainRenderPass pass, CameraTransform camera) {
        boolean translucent = pass == this.renderPassConfiguration.defaultTranslucentMaterial().pass;
        boolean opaque = pass == this.renderPassConfiguration.defaultSolidMaterial().pass;

        if (!translucent && !opaque) {
            return;
        }

        pass.startDrawing();

        if (this.isInShadowPass()) {
            if (translucent) {
                this.meshTerrain.renderShadowTranslucent(pass);
            } else if (this.shadowViewport != null) {
                int[] viewport = new int[4];
                LWJGL.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
                this.meshTerrain.renderShadow(this.shadowViewport, matrices, pass, camera.x, camera.y, camera.z, viewport[2], viewport[3]);
            }
        } else if (translucent) {
            this.meshTerrain.renderTranslucent(pass);
        } else if (this.frameViewport != null) {
            this.meshTerrain.renderOpaque(this.frameViewport, matrices, MeshFog.from(this.getFogService()), pass,
                    camera.x, camera.y, camera.z, this.getFramebufferWidth(), this.getFramebufferHeight());
        }

        pass.endDrawing();
    }

    public void trackViewport(Viewport viewport) {
        this.frameViewport = viewport;
    }

    // Returns the framebuffer width.
    protected int getFramebufferWidth() {
        return 1;
    }

    protected int getFramebufferHeight() {
        return 1;
    }

    public @Nullable MeshTerrainRenderer getMeshTerrain() {
        return this.meshTerrain;
    }

    public boolean isSectionVisible(int x, int y, int z) {
        return this.getCurrentRenderListManager().isSectionVisible(x, y, z);
    }

    private boolean rebuildListHasUpdates() {
        for (var queue : this.getCurrentRenderListManager().getRebuildLists().byUpdateType().values()) {
            if (!queue.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // Moves sections that asked for a rebuild between graph updates into their rebuild lists.
    private void promoteInterimRebuildList() {
        if (this.sectionsRequestingUpdate.isEmpty()) {
            return;
        }

        var rebuildLists = this.getCurrentRenderListManager().getRebuildLists().byUpdateType();
        boolean graphUpdatePending = this.getCurrentRenderListManager().isNeedsUpdate();

        for (var section : this.sectionsRequestingUpdate) {
            var updateType = section.getPendingUpdate();
            if (updateType == null) {
                // Skips a missing section.
                continue;
            }
            if (!graphUpdatePending || updateType.isImportant() || updateType.isSort()) {
                rebuildLists.get(updateType).add(section);
            }
        }

        this.sectionsRequestingUpdate.clear();
    }

    public void updateChunks(boolean updateImmediately) {
        this.regions.update();
        this.jobMetricsTracker.tick();

        // Ticks the scheduler on the terrain pass only.
        if (!this.isInShadowPass()) {
            this.builder.tickSchedulingBudget(this.jobMetricsTracker);
        }

        this.promoteInterimRebuildList();

        if (!rebuildListHasUpdates()) {
            if (CONTINUOUSLY_REMESH_WORLD && !this.getCurrentRenderListManager().getRebuildLists().hasAdditionalUpdates()) {
                this.scheduleRebuildAll();
            }
            return;
        }

        var blockingRebuilds = new ChunkJobCollector(Integer.MAX_VALUE, this.buildResults::add);
        var deferredRebuilds = new ChunkJobCollector(this.builder.getSchedulingBudget(), this.buildResults::add);

        this.submitRebuildTasks(blockingRebuilds, ChunkUpdateType.IMPORTANT_REBUILD);
        this.submitRebuildTasks(blockingRebuilds, ChunkUpdateType.IMPORTANT_SORT);

        this.submitRebuildTasks(updateImmediately ? blockingRebuilds : deferredRebuilds, ChunkUpdateType.REBUILD);
        this.submitRebuildTasks(updateImmediately ? blockingRebuilds : deferredRebuilds, ChunkUpdateType.INITIAL_BUILD);

        // Collects the sorts that fit in the leftover worker time.
        var deferredSorts = new ChunkJobCollector(this.builder.getSortSchedulingBudget(), this.buildResults::add);
        this.submitRebuildTasks(updateImmediately ? blockingRebuilds : deferredSorts, ChunkUpdateType.SORT);

        blockingRebuilds.awaitCompletion(this.builder);

        // Tick singlethreaded rebuilds
        this.builder.tick();
    }

    public void uploadChunks() {
        var results = this.collectChunkBuildResults();

        if (results.isEmpty()) {
            return;
        }

        // Stops the occlusion threads.
        this.finishAllGraphUpdates();

        this.processChunkBuildResults(results);

        for (var result : results) {
            result.output().delete();
        }

        // Marks the graph dirty after an update queue overflow.
        if (this.getCurrentRenderListManager().getRebuildLists().hasAdditionalUpdates()) {
            this.markGraphDirty();
        }
    }

    public final void tickVisibleRenders() {
        this.getCurrentRenderListManager().tickVisibleRenders();
    }

    private void processChunkBuildResults(ArrayList<ChunkJobResult.Success<? extends ChunkTaskOutput>> results) {
        var filtered = filterChunkBuildResults(results);

        if (this.meshTerrain != null) {
            this.uploadMeshTerrain(filtered);
        } else {
            this.regions.uploadMeshes(RenderDevice.INSTANCE.createCommandList(), filtered, this::markGraphDirty);
        }

        for (var holder : filtered) {
            var result = holder.output();

            // whether this result belongs to the most recently submitted build
            boolean latest = result.buildTime >= result.render.getLastSubmittedFrame();

            if (result instanceof ChunkBuildOutput buildResult) {
                boolean changed = this.updateSectionInfo(result.render, buildResult.info);

                if (changed) {
                    // Marks the graph dirty when the section info changed.
                    this.markGraphDirty();
                }

                // Updates the translucency info on full rebuilds.
                if (latest) {
                    this.updateTranslucencyInfo(result.render, buildResult.meshes);
                }
            }

            var job = result.render.getBuildCancellationToken();

            if (job != null && latest) {
                result.render.setBuildCancellationToken(null);
            }

            result.render.setLastBuiltFrame(result.buildTime);
            this.sectionMetricsTracker.updateSectionBuildDuration(result.render, holder.executionTimeNanos());
        }
    }

    // Uploads rebuilt geometry or updates the draw order of the resident translucent faces.
    private void uploadMeshTerrain(Collection<ChunkJobResult.Success<? extends ChunkTaskOutput>> results) {
        var translucentPass = this.renderPassConfiguration.defaultTranslucentMaterial().pass;
        for (var holder : results) {
            var result = holder.output();
            var render = result.render;
            int x = render.getChunkX(), y = render.getChunkY(), z = render.getChunkZ();

            if (result instanceof ChunkBuildOutput output) {
                var mesh = output.meshes.get(translucentPass);
                this.meshTerrain.upload(x, y, z, output.meshGeometry, translucentPass.primitiveType(),
                        mesh == null || mesh.sortState() == SortState.NONE ? null : mesh.indexBuffer());
            } else if (result instanceof ChunkSortOutput output) {
                var mesh = output.meshes.get(translucentPass);
                if (mesh != null) {
                    this.meshTerrain.uploadTranslucentOrder(x, y, z, translucentPass.primitiveType(), mesh.indexData());
                }
            }
        }
    }

    // Stores the sort states of new meshes.
    private void updateTranslucencyInfo(RenderSection render, Map<TerrainRenderPass, BuiltSectionMeshParts> meshes) {
        Map<TerrainRenderPass, SortState.Resortable> sortStates = new Reference2ObjectArrayMap<>();
        int highestIndex = RenderSection.NO_TRANSLUCENT_GEOMETRY;

        for(var entry : meshes.entrySet()) {
            if(!entry.getKey().isSorted()) {
                continue;
            }

            var state = Objects.requireNonNull(entry.getValue().sortState());

            highestIndex = Math.max(highestIndex, state.debugIndex());

            // Keeps the resortable states.
            if(state.compactForStorage() instanceof SortState.Resortable resortable) {
                sortStates.put(entry.getKey(), resortable);
            }
        }

        render.setTranslucencySortStates(sortStates.isEmpty() ? Collections.emptyMap() : sortStates, highestIndex);

        if (render.isTreeSorted()) {
            List<PartitionTree> trees = new ArrayList<>(sortStates.size());
            for (var state : sortStates.values()) {
                trees.add((PartitionTree) state);
            }

            this.cutPlaneIndex.put(render, PartitionTree.mergeCutPlanes(trees), render.getOriginX(), render.getOriginY(), render.getOriginZ());

            // Schedules a tree sort when the camera crossed a cut plane since submission.
            int ox = render.getOriginX(), oy = render.getOriginY(), oz = render.getOriginZ();
            for (PartitionTree tree : trees) {
                if (tree.crossesCutPlane(render.lastCameraX - ox, render.lastCameraY - oy, render.lastCameraZ - oz,
                        this.cameraPosition.x - ox, this.cameraPosition.y - oy, this.cameraPosition.z - oz)) {
                    this.treeTriggersThisSecond++;
                    this.scheduleSort(render);
                    break;
                }
            }
        } else {
            // a rebuild that is no longer tree-sorted must drop its stale planes
            this.cutPlaneIndex.remove(render);
        }
    }

    // Mirrors a section's packed metadata into the search lattice.
    private void pushSectionMetadata(RenderSection section) {
        this.sectionGraph.updateSectionMetadata(section.getChunkX(), section.getChunkY(), section.getChunkZ(),
                section.getPackedMetadata(), this::markGraphDirty);
    }

    @MustBeInvokedByOverriders
    protected boolean updateSectionInfo(RenderSection render, @Nullable BuiltRenderSectionData info) {
        boolean changed = render.setInfo(info);

        if (changed) {
            if (info instanceof MinecraftBuiltRenderSectionData<?, ?> data && !data.globalBlockEntities.isEmpty()) {
                this.sectionsWithGlobalEntities.add(render);
            } else {
                this.sectionsWithGlobalEntities.remove(render);
            }
        }

        return changed;
    }

    private static List<ChunkJobResult.Success<? extends ChunkTaskOutput>> filterChunkBuildResults(ArrayList<ChunkJobResult.Success<? extends ChunkTaskOutput>> outputs) {
        var map = new Reference2ReferenceLinkedOpenHashMap<RenderSection, ChunkJobResult.Success<? extends ChunkTaskOutput>>();

        for (var holder : outputs) {
            var output = holder.output();
            if (output.render.isDisposed() || output.render.getLastBuiltFrame() > output.buildTime) {
                continue;
            }

            var render = output.render;
            var previousHolder = map.get(render);

            if (previousHolder == null || previousHolder.output().buildTime < output.buildTime) {
                map.put(render, holder);
            }
        }

        return new ArrayList<>(map.values());
    }

    private ArrayList<ChunkJobResult.Success<? extends ChunkTaskOutput>> collectChunkBuildResults() {
        ArrayList<ChunkJobResult.Success<? extends ChunkTaskOutput>> results = new ArrayList<>();
        ChunkJobResult<? extends ChunkTaskOutput> result;

        while ((result = this.buildResults.poll()) != null) {
            if (result instanceof ChunkJobResult.Success<? extends ChunkTaskOutput> successfulResult) {
                this.jobMetricsTracker.collectMetrics(successfulResult);

                if (successfulResult.output() instanceof ChunkSortOutput sort && sort.render.isTreeSorted() && successfulResult.executionTimeNanos() >= 0) {
                    this.treeSortMetrics.collect(successfulResult.executionTimeNanos());
                }
                results.add(successfulResult);
            } else if (result instanceof ChunkJobResult.Failure<? extends ChunkTaskOutput> failure) {
                failure.abort();
            } else {
                throw new AssertionError();
            }
        }

        return results;
    }

    private void submitRebuildTasks(ChunkJobCollector collector, ChunkUpdateType type) {
        var queue = this.getCurrentRenderListManager().getRebuildLists().byUpdateType().get(type);

        int frame = ++this.submissionFrame;

        int cameraX = (int) Math.floor(this.cameraPosition.x);
        int cameraY = (int) Math.floor(this.cameraPosition.y);
        int cameraZ = (int) Math.floor(this.cameraPosition.z);

        while (!queue.isEmpty() && collector.canOffer()) {
            RenderSection section = queue.remove();

            if (section.isDisposed()) {
                continue;
            }

            // Skips entries whose pending type changed since queueing.
            if (section.getPendingUpdate() != type) {
                continue;
            }

            ChunkBuilderTask<? extends ChunkTaskOutput> task = type.isSort() ? this.createSortTask(section, frame) : this.createRebuildTask(section, frame);

            if (task == null && type.isSort()) {
                // Ignore sorts that became invalid
                section.setPendingUpdate(null);
                continue;
            }

            if (task != null) {
                // Prioritizes by distance.
                long priority = (long) section.getSquaredDistanceFromBlockCenter(cameraX, cameraY, cameraZ);
                var job = this.builder.scheduleTask(task, type.isImportant(), priority, collector::onJobFinished);
                collector.addSubmittedJob(job);

                section.setBuildCancellationToken(job);

                if (!type.isSort()) {
                    // Prevent further sorts from being performed on this section
                    section.clearTranslucencySortStates();
                    this.cutPlaneIndex.remove(section);
                }
                // Both meshing and sorting use the camera at submission.
                section.lastCameraX = this.cameraPosition.x;
                section.lastCameraY = this.cameraPosition.y;
                section.lastCameraZ = this.cameraPosition.z;
            } else {
                var result = new ChunkJobResult.Success<>(new ChunkBuildOutput(section, RenderSection.EMPTY_DATA, Reference2ReferenceMaps.emptyMap(), frame), -1);
                this.buildResults.add(result);

                section.setBuildCancellationToken(null);
            }

            section.setLastSubmittedFrame(frame);
            section.setPendingUpdate(null);
        }
    }

    protected abstract @Nullable ChunkBuilderTask<ChunkBuildOutput> createRebuildTask(RenderSection render, int frame);

    public ChunkBuilderSortTask createSortTask(RenderSection render, int frame) {
        if(render.getTranslucencySortStates().isEmpty())
            return null;
        return new ChunkBuilderSortTask(render, cameraPosition.x, cameraPosition.y, cameraPosition.z, frame, render.getTranslucencySortStates(), this.renderPassConfiguration);
    }

    public void markGraphDirty() {
        if (this.shadowRenderListManager != null) {
            this.shadowRenderListManager.setNeedsUpdate(true);
        }
        this.renderListManager.setNeedsUpdate(true);
    }

    public void finishAllGraphUpdates() {
        this.renderListManager.finishPreviousGraphUpdate();
        if (this.shadowRenderListManager != null) {
            this.shadowRenderListManager.finishPreviousGraphUpdate();
        }
    }

    // Returns whether update must run in this pass.
    public boolean needsUpdate() {
        if (this.isInShadowPass()) {
            return this.shadowRenderListManager.isNeedsUpdate();
        }
        return this.renderListManager.isNeedsUpdate() || this.shadowPassRanThisFrame;
    }

    public ChunkBuilder getBuilder() {
        return this.builder;
    }

    public void destroy() {
        this.finishAllGraphUpdates();

        this.builder.shutdown(); // stops the workers and cancels the tasks

        for (var result : this.collectChunkBuildResults()) {
            result.output().delete(); // delete resources for any pending tasks (including those that were cancelled)
        }

        this.renderListManager.destroy();
        if (this.shadowRenderListManager != null) {
            this.shadowRenderListManager.destroy();
        }
        this.sectionGraph.destroy();

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.regions.delete(commandList);
            this.chunkRenderer.delete(commandList);
        }

        this.renderPassDrawTimers.values().forEach(TimerQueryManager::close);
        this.renderPassDrawTimers.clear();

        this.sectionsWithGlobalEntities.clear();

        if (this.meshTerrain != null) {
            this.meshTerrain.delete();
        }
    }

    public int getTotalSections() {
        return this.sectionByPosition.size();
    }

    public int getVisibleChunkCount() {
        var sections = 0;
        var iterator = this.getCurrentRenderListManager().getRenderLists().iterator();

        while (iterator.hasNext()) {
            var renderList = iterator.next();
            sections += renderList.getSectionsWithGeometryCount();
        }

        return sections;
    }

    public final void scheduleAsyncTask(Runnable runnable) {
        if (Thread.currentThread() == this.renderThread) {
            // Runs the task on this thread.
            runnable.run();
        } else {
            asyncSubmittedTasks.add(runnable);
        }
    }

    private void scheduleRebuildOffThread(int x, int y, int z, boolean important) {
        scheduleAsyncTask(() -> this.scheduleSectionForRebuild(x, y, z, important));
    }

    public final void scheduleRebuild(int x, int y, int z, boolean important) {
        if (Thread.currentThread() != renderThread) {
            this.scheduleRebuildOffThread(x, y, z, important);
            return;
        }

        this.scheduleSectionForRebuild(x, y, z, important);
    }

    protected void invalidateCachedSectionData(RenderSection section) {

    }

    protected void scheduleSectionForRebuild(int x, int y, int z, boolean important) {
        RenderSection section = this.sectionByPosition.get(PositionUtil.packSection(x, y, z));

        if (section != null) {
            this.invalidateCachedSectionData(section);

            ChunkUpdateType pendingUpdate;

            if (allowImportantRebuilds() && (important || this.shouldPrioritizeRebuild(section))) {
                pendingUpdate = ChunkUpdateType.IMPORTANT_REBUILD;
            } else {
                pendingUpdate = ChunkUpdateType.REBUILD;
            }

            if (section.requestUpdate(pendingUpdate)) {
                // Reads the importance from the section's new update type.
                important = section.getPendingUpdate().isImportant();

                if (important ||
                        (!this.getCurrentRenderListManager().isNeedsUpdate() &&
                            this.sectionsRequestingUpdate.size() < this.builder.getSchedulingBudget())) {
                    this.sectionsRequestingUpdate.add(section);
                } else {
                    this.markGraphDirty();
                }
            }
        }
    }

    public void scheduleRebuildAll() {
        for (var section : this.sectionByPosition.values()) {
            if (!this.isSectionVisuallyEmpty(section.getChunkX(), section.getChunkY(), section.getChunkZ())) {
                this.invalidateCachedSectionData(section);
                section.requestUpdate(ChunkUpdateType.REBUILD);
            }
        }
        this.markGraphDirty();
    }

    private static final float NEARBY_REBUILD_DISTANCE = MathUtil.square(16.0f);

    private boolean shouldPrioritizeRebuild(RenderSection section) {
        return this.cameraBlockPosition != null && section.getSquaredDistanceFromBlockCenter(this.cameraBlockPosition.x(), this.cameraBlockPosition.y(), this.cameraBlockPosition.z()) < NEARBY_REBUILD_DISTANCE;
    }

    // Returns whether rebuilds near the player block the main thread.
    protected boolean allowImportantRebuilds() {
        return false;
    }

    private float getEffectiveRenderDistance(@Nullable Matrix4fc projectionMatrix) {
        var color = this.getFogService().getFogColor();
        var alpha = color[3];
        var distance = this.getFogService().getFogCutoff();
        var shape = this.getFogService().getFogShapeIndex();

        var renderDistance = this.getRenderDistance();

        // The fog must be fully opaque in order to skip rendering of chunks behind it
        if (Math.abs(alpha - 1.0f) >= 1.0E-5F) {
            return renderDistance;
        }

        if (shape == FogService.FOG_SHAPE_PLANAR) {
            // Scales the cutoff by the frustum secant for planar fog.
            var secant = getMaximumFrustumSecant(projectionMatrix);

            if (secant == 0.0f) {
                // Returns the render distance for an unknown projection.
                return renderDistance;
            }

            distance *= secant;
        }

        return Math.min(renderDistance, distance + 0.5f);
    }

    // Returns the largest ratio of camera distance to view depth inside the frustum.
    private static float getMaximumFrustumSecant(@Nullable Matrix4fc projectionMatrix) {
        if (projectionMatrix == null) {
            return 0.0f;
        }

        // Reads the perspective divide term.
        float w = Math.abs(projectionMatrix.m23());

        if (w == 0.0f) {
            return 0.0f;
        }

        float tanX = (w + Math.abs(projectionMatrix.m20())) / Math.abs(projectionMatrix.m00());
        float tanY = (w + Math.abs(projectionMatrix.m21())) / Math.abs(projectionMatrix.m11());

        float secant = (float)Math.sqrt(1.0 + (tanX * tanX) + (tanY * tanY));

        // Rejects a degenerate projection.
        if (!(secant >= 1.0f) || !Float.isFinite(secant)) {
            return 0.0f;
        }

        return secant;
    }

    private float getRenderDistance() {
        return this.renderDistance * 16.0f;
    }

    private RenderSection getRenderSection(int x, int y, int z) {
        return this.sectionByPosition.get(PositionUtil.packSection(x, y, z));
    }

    public Collection<RenderSection> getAllRenderSections() {
        return Collections.unmodifiableCollection(this.sectionByPosition.values());
    }

    private Object2LongMap<TerrainRenderPass> computeRenderPassTimingsMap() {
        Object2LongOpenHashMap<TerrainRenderPass> map = new Object2LongOpenHashMap<>();
        for (var entry : renderPassDrawTimers.entrySet()) {
            map.put(entry.getKey(), entry.getValue().getLastTime());
        }
        return map;
    }

    protected final Supplier<Object2LongMap<TerrainRenderPass>> renderPassTimingsDebounced = new ExpiringSupplier<>(this::computeRenderPassTimingsMap, 1, TimeUnit.SECONDS);

    public Collection<String> getDebugStrings() {
        List<String> list = new ArrayList<>();

        if (this.meshTerrain != null) {
            this.meshTerrain.addDebugStrings(list);
        }

        int count = 0, indexCount = 0;

        long deviceUsed = 0;
        long deviceAllocated = 0;

        long indexUsed = 0, indexAllocated = 0;

        for (var region : this.regions.getLoadedRegions()) {
            var resources = region.getResources();

            if (resources == null) {
                continue;
            }

            var buffer = resources.getGeometryArena();

            deviceUsed += buffer.getDeviceUsedMemoryL();
            deviceAllocated += buffer.getDeviceAllocatedMemoryL();

            var indexBuffer = resources.getIndexArena();

            if (indexBuffer != null) {
                indexUsed += indexBuffer.getDeviceUsedMemoryL();
                indexAllocated += indexBuffer.getDeviceAllocatedMemoryL();
                indexCount++;
            }

            count++;
        }

        list.add(String.format("G: %d/%d, I: %d/%d MiB (%d buffers)", MathUtil.toMib(deviceUsed), MathUtil.toMib(deviceAllocated), MathUtil.toMib(indexUsed), MathUtil.toMib(indexAllocated), count));
        list.add(String.format("Transfer Queue: %s", this.regions.getStagingBuffer().toString()));

        var uploadEstimator = this.regions.getUploadDurationEstimator();
        long lastUploadBytes = uploadEstimator.getLastUploadBytes();
        if (lastUploadBytes > 0L) {
            list.add(String.format("Upload Estimate: %d KiB, predicted %s, last %s",
                    lastUploadBytes / 1024L,
                    TimeUtil.stringifyTime(uploadEstimator.getLastUploadEstimateNanos(), TimeUnit.NANOSECONDS),
                    TimeUtil.stringifyTime(uploadEstimator.getLastUploadDurationNanos(), TimeUnit.NANOSECONDS)));
        }

        var rebuildLists = this.getCurrentRenderListManager().getRebuildLists();

        list.add(String.format("Chunk Queues: U=%02d (P0=%03d | P1=%03d | P2=%03d) S=%03d/%03d",
                this.buildResults.size(),
                rebuildLists.getUpdateCount(ChunkUpdateType.IMPORTANT_REBUILD),
                rebuildLists.getUpdateCount(ChunkUpdateType.REBUILD),
                rebuildLists.getUpdateCount(ChunkUpdateType.INITIAL_BUILD),
                rebuildLists.getUpdateCount(ChunkUpdateType.IMPORTANT_SORT),
                rebuildLists.getUpdateCount(ChunkUpdateType.SORT)
        ));

        var debugStats = renderListManager.getDebugStatistics();

        var counts = debugStats.renderPassCounts().object2IntEntrySet().stream().sorted(Comparator.comparingInt(e -> -e.getIntValue())).iterator();

        var timingMap = renderPassTimingsDebounced.get();

        while (counts.hasNext()) {
            var entry = counts.next();
            var duration = timingMap.getLong(entry.getKey());
            String time;
            if (duration == 0) {
                time = "?? ms";
            } else {
                time = TimeUtil.stringifyTime(duration, TimeUnit.NANOSECONDS);
            }

            list.add(entry.getKey().name() + " - " + entry.getIntValue() + " sections, " + time);
        }

        boolean sortFaces = this.renderPassConfiguration.defaultTranslucentMaterial().pass.isSorted();
        list.add(sortFaces ? "Translucency sorting: On" : "Translucency sorting: Off");
        if (sortFaces) {
            list.add(debugStats.getSortingString());
            list.add(String.format("Tree Sort: %d planes, %d trig/s, %d sorts/s, %s avg",
                    this.cutPlaneIndex.planeCount(), this.treeTriggersLastSecond,
                    this.treeSortMetrics.getObservationsInLastTimeInterval(),
                    TimeUtil.stringifyTime((long) this.treeSortMetrics.getAverageNanos(0), TimeUnit.NANOSECONDS)));
        }

        return list;
    }

    private RenderListManager getCurrentRenderListManager() {
        return isInShadowPass() ? this.shadowRenderListManager : this.renderListManager;
    }

    public SortedRenderLists getRenderLists() {
        return this.getCurrentRenderListManager().getRenderLists();
    }

    public boolean isSectionBuilt(int x, int y, int z) {
        var section = this.getRenderSection(x, y, z);
        return section != null && section.isBuilt();
    }

    public void onChunkAdded(int x, int z) {
        for (int y = this.minSection; y < this.maxSection; y++) {
            this.onSectionAdded(x, y, z);
        }
    }

    public void onChunkRemoved(int x, int z) {
        for (int y = this.minSection; y < this.maxSection; y++) {
            this.onSectionRemoved(x, y, z);
        }
    }

    public void toggleRenderingForTerrainPass(TerrainRenderPass pass) {
        if (!this.disabledRenderPasses.add(pass)) {
            this.disabledRenderPasses.remove(pass);
        }
    }

    public final Collection<RenderSection> getSectionsWithGlobalEntities() {
        return ReferenceSets.unmodifiable(this.sectionsWithGlobalEntities);
    }

    public String getTickerDebugString() {
        return this.getCurrentRenderListManager().getTickerDebugString();
    }
}
