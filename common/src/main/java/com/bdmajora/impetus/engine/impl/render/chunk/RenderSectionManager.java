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
import com.bdmajora.impetus.engine.impl.render.chunk.lists.ChunkRenderList;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.RenderListManager;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.SectionTicker;
import com.bdmajora.impetus.engine.impl.render.chunk.lists.SortedRenderLists;
import com.bdmajora.impetus.engine.impl.render.chunk.metrics.RenderSectionMetricsTracker;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.AsyncOcclusionMode;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.VisibilityEncoding;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;
import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegionManager;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.ChunkShaderFogComponent;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.viewport.CameraTransform;
import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import com.bdmajora.impetus.engine.impl.util.iterator.ByteIterator;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.TranslucentQuadAnalyzer;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.trigger.NormalPlanes;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.trigger.TranslucencyTriggerIndex;
import com.bdmajora.impetus.engine.impl.util.suppliers.ExpiringSupplier;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3ic;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public abstract class RenderSectionManager {
    // When true, all sections are continuously marked for remeshing whenever the update queue empties
    protected static final boolean CONTINUOUSLY_REMESH_WORLD = false;

    private final ChunkBuilder builder;

    private final Thread renderThread = Thread.currentThread();

    private final RenderRegionManager regions;

    private final Long2ReferenceMap<RenderSection> sectionByPosition = new Long2ReferenceOpenHashMap<>();

    private final ConcurrentLinkedDeque<ChunkJobResult<? extends ChunkTaskOutput>> buildResults = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Runnable> asyncSubmittedTasks = new ConcurrentLinkedDeque<>();

    private final ChunkRenderer chunkRenderer;

    private final int renderDistance;

    protected @Nullable Vector3ic lastCameraPosition;
    protected final Vector3d cameraPosition = new Vector3d();

    // Maps translucent geometry planes to their owning sections so camera movement re-sorts only where draw order can actually have changed
    private final TranslucencyTriggerIndex translucencyTriggerIndex = new TranslucencyTriggerIndex();

    // Dynamic sections without usable plane data (normal-count overflow) keep the legacy coarse movement-based re-sort heuristic
    private final ReferenceOpenHashSet<RenderSection> coarseTriggeredSections = new ReferenceOpenHashSet<>();

    // The camera position the trigger planes were last tested from; unset until the first camera-pass update
    private final Vector3d lastTriggerCameraPosition = new Vector3d();
    private boolean hasTriggerCameraPosition;

    // Movement (squared) beyond which we skip plane tests and re-sort every dynamic section (teleports).
    private static final double TELEPORT_DISTANCE_SQ = 16.0 * 16.0;

    @Getter
    private final RenderPassConfiguration<?> renderPassConfiguration;

    private final Set<TerrainRenderPass> disabledRenderPasses;

    private final int minSection, maxSection;

    protected final RenderListManager renderListManager;

    @Nullable
    protected final RenderListManager shadowRenderListManager;

    protected final ReferenceSet<RenderSection> sectionsWithGlobalEntities = new ReferenceOpenHashSet<>();

    private final Object2ObjectOpenHashMap<TerrainRenderPass, TimerQueryManager> renderPassDrawTimers = new Object2ObjectOpenHashMap<>();

    protected final ReferenceSet<RenderSection> sectionsRequestingUpdate = new ReferenceOpenHashSet<>();

    @Getter
    protected final ChunkJobMetricsTracker jobMetricsTracker = new ChunkJobMetricsTracker();

    @Getter
    protected final RenderSectionMetricsTracker sectionMetricsTracker = new RenderSectionMetricsTracker();

    public RenderSectionManager(RenderPassConfiguration<?> configuration, Supplier<ChunkBuildContext> contextSupplier,
                                BiFunction<RenderDevice, RenderPassConfiguration<?>, ChunkRenderer> chunkRenderer,
                                int renderDistance, CommandList commandList, int minSection, int maxSection,
                                int requestedThreads, boolean hasShadowPass) {
        this.chunkRenderer = chunkRenderer.apply(RenderDevice.INSTANCE, configuration);

        this.renderPassConfiguration = configuration;

        this.builder = new ChunkBuilder(this::managedBlock, contextSupplier, requestedThreads);

        this.renderDistance = renderDistance;

        this.regions = new RenderRegionManager(commandList);

        this.minSection = minSection;
        this.maxSection = maxSection;
        this.renderListManager = new RenderListManager(this.minSection, this.maxSection, this.getAsyncOcclusionMode() == AsyncOcclusionMode.EVERYTHING, this.createSectionTicker());
        if (hasShadowPass) {
            this.shadowRenderListManager = new RenderListManager(this.minSection, this.maxSection, this.getAsyncOcclusionMode() != AsyncOcclusionMode.NONE, this.createSectionTicker());
        } else {
            this.shadowRenderListManager = null;
        }

        this.disabledRenderPasses = new ReferenceArraySet<>();
    }

    protected abstract AsyncOcclusionMode getAsyncOcclusionMode();

    protected @Nullable SectionTicker createSectionTicker() {
        return null;
    }

    // Waits productively by running build jobs on this thread
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

    // Drains work other threads asked the render thread to do
    public void runAsyncTasks() {
        Runnable task;

        while ((task = this.asyncSubmittedTasks.poll()) != null) {
            task.run();
        }

        this.renderPassDrawTimers.values().forEach(TimerQueryManager::updateTime);
    }

    // whether terrain is being rendered for shadows
    public boolean isInShadowPass() {
        return false;
    }

    // Platform hook; gates the expensive debug strings
    protected boolean isDebugInfoShown() {
        return false;
    }

    // Per-frame: check translucency triggers, then rebuild the render list
    public void update(Viewport positionedViewport, int frame, boolean spectator) {
        if (isInShadowPass()) {
            // Umbra parity: the shadow pass runs first every frame and must not write cameraPosition/lastCameraPosition (shared with the camera pass), or rebuild priority and sort distances measure against the SHADOW viewport
            this.createTerrainRenderList(positionedViewport, frame, spectator);
            return;
        }

        this.lastCameraPosition = positionedViewport.getBlockCoord();
        var transform = positionedViewport.getTransform();
        this.cameraPosition.set(transform.x, transform.y, transform.z);

        this.createTerrainRenderList(positionedViewport, frame, spectator);

        this.checkTranslucencyChange();

        this.getCurrentRenderListManager().setNeedsUpdate(false);
    }

    // Detects the camera crossing a trigger plane and schedules re-sorts
    private void checkTranslucencyChange() {
        if(lastCameraPosition == null)
            return;

        Vector3d previous = this.lastTriggerCameraPosition;
        if (this.hasTriggerCameraPosition && !previous.equals(this.cameraPosition)) {
            if (previous.distanceSquared(this.cameraPosition) > TELEPORT_DISTANCE_SQ) {
                // Large jumps (teleports, dimension-ish moves) cross too many planes to be worth testing.
                this.translucencyTriggerIndex.forEachSection(section -> section.pendingTriggeredSort = true);
            } else {
                this.translucencyTriggerIndex.collectTriggered(
                        previous.x, previous.y, previous.z,
                        this.cameraPosition.x, this.cameraPosition.y, this.cameraPosition.z,
                        section -> section.pendingTriggeredSort = true);
            }
        }
        previous.set(this.cameraPosition);
        this.hasTriggerCameraPosition = true;

        int camSectionX = PositionUtil.posToSectionCoord(cameraPosition.x);
        int camSectionY = PositionUtil.posToSectionCoord(cameraPosition.y);
        int camSectionZ = PositionUtil.posToSectionCoord(cameraPosition.z);

        this.scheduleTranslucencyUpdates(camSectionX, camSectionY, camSectionZ);
    }

    // Queues a sort task for every section the camera's move invalidated
    private void scheduleTranslucencyUpdates(int camSectionX, int camSectionY, int camSectionZ) {
        if (!this.hasTranslucencySortedSections()) {
            return;
        }
        var renderListManager = this.getCurrentRenderListManager();
        var rebuildLists = renderListManager.getRebuildLists().byUpdateType();
        var sortRebuildList = rebuildLists.get(ChunkUpdateType.SORT);
        var importantSortRebuildList = rebuildLists.get(ChunkUpdateType.IMPORTANT_SORT);
        var allowImportant = allowImportantRebuilds();
        var translucentPass = this.renderPassConfiguration.defaultTranslucentMaterial().pass;
        for (Iterator<ChunkRenderList> it = renderListManager.getRenderLists().iterator(); it.hasNext(); ) {
            ChunkRenderList entry = it.next();
            var region = entry.getRegion();
            if (!region.hasSectionsInPass(translucentPass)) {
                continue;
            }
            ByteIterator sectionIterator = entry.sectionsWithGeometryIterator(false);
            if (sectionIterator == null) {
                continue;
            }
            while (sectionIterator.hasNext()) {
                var section = region.getSection(sectionIterator.nextByteAsInt());

                if (section == null || !section.isNeedsDynamicTranslucencySorting()) {
                    // Sections without sortable translucent data are not relevant
                    continue;
                }

                ChunkUpdateType update = ChunkUpdateType.getPromotionUpdateType(section.getPendingUpdate(), (allowImportant && this.shouldPrioritizeRebuild(section)) ? ChunkUpdateType.IMPORTANT_SORT : ChunkUpdateType.SORT);

                if (update == null) {
                    // We wouldn't be able to resort this section anyway
                    continue;
                }

                boolean triggered = section.pendingTriggeredSort;

                if (!triggered && this.coarseTriggeredSections.contains(section)) {
                    // Legacy heuristic for sections whose plane data overflowed: re-sort after moving at least one block across the section grid or its axes
                    double dx = cameraPosition.x - section.lastCameraX;
                    double dy = cameraPosition.y - section.lastCameraY;
                    double dz = cameraPosition.z - section.lastCameraZ;
                    double camDelta = (dx * dx) + (dy * dy) + (dz * dz);

                    if (camDelta >= 1) {
                        boolean cameraChangedSection = camSectionX != PositionUtil.posToSectionCoord(section.lastCameraX) ||
                                camSectionY != PositionUtil.posToSectionCoord(section.lastCameraY) ||
                                camSectionZ != PositionUtil.posToSectionCoord(section.lastCameraZ);

                        triggered = cameraChangedSection || section.isAlignedWithSectionOnGrid(camSectionX, camSectionY, camSectionZ);
                    }
                }

                if (triggered) {
                    section.setPendingUpdate(update);
                    // Inject it into the rebuild lists
                    (update == ChunkUpdateType.IMPORTANT_SORT ? importantSortRebuildList : sortRebuildList).add(section);

                    section.pendingTriggeredSort = false;
                    section.lastCameraX = cameraPosition.x;
                    section.lastCameraY = cameraPosition.y;
                    section.lastCameraZ = cameraPosition.z;
                }
            }
        }
    }

    // True if the renderer should respect per-frame queue limits rather than updating as many chunks as possible
    protected boolean shouldRespectUpdateTaskQueueSizeLimit() {
        return true;
    }

    // Starts the occlusion walk for this frame
    private void createTerrainRenderList(Viewport viewport, int frame, boolean spectator) {
        final var searchDistance = this.getSearchDistance();
        final var useOcclusionCulling = this.shouldUseOcclusionCulling(viewport, spectator);
        final int targetQueueSize;

        if (this.shouldRespectUpdateTaskQueueSizeLimit()) {
            targetQueueSize = (int)Math.min(Integer.MAX_VALUE, (long)this.builder.getTargetQueueSize() * 10);
        } else {
            targetQueueSize = Integer.MAX_VALUE;
        }

        this.getCurrentRenderListManager().startGraphUpdate(viewport, frame, this.regions.getRegionIdsLength(),
                searchDistance, useOcclusionCulling, targetQueueSize);
    }

    protected abstract boolean useFogOcclusion();

    // Render distance in blocks, extended by fog occlusion when enabled
    private float getSearchDistance() {
        float distance;

        if (this.useFogOcclusion()) {
            distance = this.getEffectiveRenderDistance();
        } else {
            distance = this.getRenderDistance();
        }

        return distance;
    }

    protected abstract boolean shouldUseOcclusionCulling(Viewport viewport, boolean spectator);

    // Whether any section needs dynamic sorting at all
    private boolean hasTranslucencySortedSections() {
        return this.getCurrentRenderListManager().getRenderLists().hasSortedPass();
    }

    protected abstract boolean isSectionVisuallyEmpty(int x, int y, int z);

    // Creates a section, attaches it to its region and the graph, and queues its first build
    public void onSectionAdded(int x, int y, int z) {
        long key = PositionUtil.packSection(x, y, z);

        if (this.sectionByPosition.containsKey(key)) {
            return;
        }

        RenderRegion region = this.regions.createForChunk(x, y, z);

        RenderSection renderSection = new RenderSection(region, x, y, z);
        region.addSection(renderSection);

        this.sectionByPosition.put(key, renderSection);

        this.renderListManager.attachRenderSection(renderSection);
        if (this.shadowRenderListManager != null) {
            this.shadowRenderListManager.attachRenderSection(renderSection);
        }

        this.invalidateCachedSectionData(renderSection);

        if (this.isSectionVisuallyEmpty(x, y, z)) {
            this.updateSectionInfo(renderSection, RenderSection.EMPTY_DATA);
        } else {
            renderSection.setPendingUpdate(ChunkUpdateType.INITIAL_BUILD);
        }

        this.markGraphDirty();
    }

    // Detaches, cancels any build and frees the section
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

        this.renderListManager.detachRenderSection(section);
        if (this.shadowRenderListManager != null) {
            this.shadowRenderListManager.detachRenderSection(section);
        }

        this.sectionMetricsTracker.removeSection(section);

        this.translucencyTriggerIndex.remove(section);
        this.coarseTriggeredSections.remove(section);

        section.delete();

        this.markGraphDirty();
    }

    // Draws one pass over the current render lists
    public void renderLayer(ChunkRenderMatrices matrices, TerrainRenderPass pass, CameraTransform occlusionCamera, CameraTransform camera) {
        if (disabledRenderPasses.contains(pass)) {
            return;
        }

        RenderDevice device = RenderDevice.INSTANCE;
        CommandList commandList = device.createCommandList();

        // Not in the shadow pass: it draws the same passes through this method, and sharing one timer per pass between the two enqueued two query pairs a frame while updateTime dequeued one, so the in-flight queue (and its GL query objects) grew by a pair every frame F3 was open
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

    // Whether the last walk reached the section
    public boolean isSectionVisible(int x, int y, int z) {
        return this.getCurrentRenderListManager().isSectionVisible(x, y, z);
    }

    // Anything queued for rebuild
    private boolean rebuildListHasUpdates() {
        for (var queue : this.getCurrentRenderListManager().getRebuildLists().byUpdateType().values()) {
            if (!queue.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // injects sections that requested a rebuild between graph updates into the appropriate rebuild lists
    private void promoteInterimRebuildList() {
        var rebuildLists = this.getCurrentRenderListManager().getRebuildLists().byUpdateType();
        for (var section : this.sectionsRequestingUpdate) {
            rebuildLists.get(section.getPendingUpdate()).add(section);
        }
    }

    // Submits rebuilds under the frame budget, running important ones on this thread
    public void updateChunks(boolean updateImmediately) {
        this.regions.update();
        this.jobMetricsTracker.tick();

        // Advance the adaptive scheduling controller once per frame on the main pass only, so an extra shadow pass sharing the worker queue does not double-tick it
        boolean mainPass = !this.isInShadowPass();

        if (mainPass) {
            this.builder.tickSchedulingBudget();
        }

        // Main pass only: sectionsRequestingUpdate is main-pass state and a graph update regenerates it anyway; the guard is a backstop, since draining it from the shadow pass would spend the shared ChunkBuilder budget and starve terrain
        if (mainPass) {
            if (!this.renderListManager.isNeedsUpdate() && !sectionsRequestingUpdate.isEmpty()) {
                this.promoteInterimRebuildList();
            }

            this.sectionsRequestingUpdate.clear();
        }

        if (!rebuildListHasUpdates()) {
            // Nothing was dispatched, so the workers cannot have been starved for lack of budget.
            if (mainPass) {
                this.builder.setDispatchBudgetLimited(false);
            }
            if (CONTINUOUSLY_REMESH_WORLD && !this.getCurrentRenderListManager().getRebuildLists().hasAdditionalUpdates()) {
                this.scheduleRebuildAll();
            }
            return;
        }

        var blockingRebuilds = new ChunkJobCollector(Integer.MAX_VALUE, this.buildResults::add);
        var deferredRebuilds = new ChunkJobCollector(this.builder.getSchedulingBudget(), this.buildResults::add);

        this.submitRebuildTasks(blockingRebuilds, ChunkUpdateType.IMPORTANT_REBUILD);
        this.submitRebuildTasks(blockingRebuilds, ChunkUpdateType.IMPORTANT_SORT);

        // Track whether deferred dispatch was throttled by the budget while work remained; with worker starvation this tells the controller to grow the in-flight target
        boolean budgetLimited = false;
        budgetLimited |= this.submitRebuildTasks(updateImmediately ? blockingRebuilds : deferredRebuilds, ChunkUpdateType.REBUILD);
        budgetLimited |= this.submitRebuildTasks(updateImmediately ? blockingRebuilds : deferredRebuilds, ChunkUpdateType.INITIAL_BUILD);
        // Candidates the BFS discarded for not fitting the rebuild lists also count as work we could not dispatch this frame
        budgetLimited |= this.getCurrentRenderListManager().getRebuildLists().hasAdditionalUpdates();
        if (mainPass) {
            this.builder.setDispatchBudgetLimited(budgetLimited);
        }

        // Count sort tasks as requiring a quarter of the resources of a mesh task
        var deferredSorts = new ChunkJobCollector(Math.max(4, this.builder.getSchedulingBudget() * 4), this.buildResults::add);
        this.submitRebuildTasks(updateImmediately ? blockingRebuilds : deferredSorts, ChunkUpdateType.SORT);

        blockingRebuilds.awaitCompletion(this.builder);

        // Tick singlethreaded rebuilds
        this.builder.tick();
    }

    // Collects finished builds and uploads them
    public void uploadChunks() {
        var results = this.collectChunkBuildResults();

        if (results.isEmpty()) {
            return;
        }

        // Ensure occlusion threads are stopped at this point, as we're about to mutate render section data.
        this.finishAllGraphUpdates();

        this.processChunkBuildResults(results);

        for (var result : results) {
            result.output().delete();
        }

        // Force a graph update if the previous render list overflowed the update queue, so those additional chunks get queued
        if (this.getCurrentRenderListManager().getRebuildLists().hasAdditionalUpdates()) {
            this.markGraphDirty();
        }
    }

    // Advances sprite animation
    public final void tickVisibleRenders() {
        this.getCurrentRenderListManager().tickVisibleRenders();
    }

    // Installs each result's section data and queues its meshes
    private void processChunkBuildResults(ArrayList<ChunkJobResult.Success<? extends ChunkTaskOutput>> results) {
        var filtered = filterChunkBuildResults(results);

        this.regions.uploadMeshes(RenderDevice.INSTANCE.createCommandList(), filtered, this::markGraphDirty);

        for (var holder : filtered) {
            var result = holder.output();
            if (result instanceof ChunkBuildOutput buildResult) {
                boolean changed = this.updateSectionInfo(result.render, buildResult.info);

                if (changed) {
                    // Rebuild the chunk graph when the section reports changed info (occlusion data, block entity add/remove, animated texture change, etc.)
                    this.markGraphDirty();
                }

                // We only change the translucency info on full rebuilds, as sorts can keep using the same data
                this.updateTranslucencyInfo(result.render, buildResult.meshes);
            }

            var job = result.render.getBuildCancellationToken();

            // Only clear the token if this result belongs to the most recent submission; a stale result must not clear a newer in-flight job's token
            if (job != null && result.buildTime >= result.render.getLastSubmittedFrame()) {
                result.render.setBuildCancellationToken(null);
            }

            result.render.setLastBuiltFrame(result.buildTime);
            this.sectionMetricsTracker.updateSectionBuildDuration(result.render, holder.executionTimeNanos());
        }
    }

    // Registers or clears the section's trigger planes
    private void updateTranslucencyInfo(RenderSection render, Map<TerrainRenderPass, BuiltSectionMeshParts> meshes) {
        Map<TerrainRenderPass, TranslucentQuadAnalyzer.SortState> sortStates = new Reference2ObjectArrayMap<>();
        for(var entry : meshes.entrySet()) {
            if(entry.getKey().isSorted()) {
                sortStates.put(entry.getKey(), Objects.requireNonNull(entry.getValue().sortState()).compactForStorage());
            }
        }
        render.setTranslucencySortStates(sortStates);

        this.updateTranslucencyTriggerRegistration(render, sortStates);
    }

    // (Re-)registers a section with the plane-crossing trigger index; overflowed or pre-mechanism plane data falls back to the legacy movement heuristic
    private void updateTranslucencyTriggerRegistration(RenderSection render, Map<TerrainRenderPass, TranslucentQuadAnalyzer.SortState> sortStates) {
        NormalPlanes[] planes = null;
        boolean dynamic = false;
        boolean missingPlanes = false;

        for (var state : sortStates.values()) {
            if (!state.requiresDynamicSorting()) {
                continue;
            }

            dynamic = true;

            var statePlanes = state.triggerPlanes();

            if (statePlanes == null) {
                missingPlanes = true;
            } else if (planes == null) {
                planes = statePlanes;
            } else {
                var merged = new NormalPlanes[planes.length + statePlanes.length];
                System.arraycopy(planes, 0, merged, 0, planes.length);
                System.arraycopy(statePlanes, 0, merged, planes.length, statePlanes.length);
                planes = merged;
            }
        }

        render.pendingTriggeredSort = false;

        boolean indexed = dynamic && !missingPlanes && planes != null;
        if (indexed) {
            this.translucencyTriggerIndex.update(render, planes);
        } else {
            this.translucencyTriggerIndex.remove(render);
        }
        // A dynamic section the index cannot cover falls back to the coarse movement heuristic
        if (dynamic && !indexed) {
            this.coarseTriggeredSections.add(render);
        } else {
            this.coarseTriggeredSections.remove(render);
        }
    }

    // Installs built data; true when visibility changed and the graph needs a walk
    @MustBeInvokedByOverriders
    protected boolean updateSectionInfo(RenderSection render, @Nullable BuiltRenderSectionData info) {
        boolean changed = render.setInfo(info);

        if (changed) {
            long visibilityData = info != null ? info.visibilityData : VisibilityEncoding.NULL;
            this.renderListManager.updateVisibilityData(render.getChunkX(), render.getChunkY(), render.getChunkZ(), visibilityData);
            if (this.shadowRenderListManager != null) {
                this.shadowRenderListManager.updateVisibilityData(render.getChunkX(), render.getChunkY(), render.getChunkZ(), visibilityData);
            }

            if (!(info instanceof MinecraftBuiltRenderSectionData<?, ?> data)) {
                this.sectionsWithGlobalEntities.remove(render);
            } else if (!data.globalBlockEntities.isEmpty()) {
                this.sectionsWithGlobalEntities.add(render);
            }
        }

        return changed;
    }

    // Drops results for sections rebuilt again since, keeping only the newest
    private static Collection<ChunkJobResult.Success<? extends ChunkTaskOutput>> filterChunkBuildResults(ArrayList<ChunkJobResult.Success<? extends ChunkTaskOutput>> outputs) {
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

        return map.values();
    }

    // Drains the builder's finished jobs, aborting failures
    private ArrayList<ChunkJobResult.Success<? extends ChunkTaskOutput>> collectChunkBuildResults() {
        ArrayList<ChunkJobResult.Success<? extends ChunkTaskOutput>> results = new ArrayList<>();
        ChunkJobResult<? extends ChunkTaskOutput> result;

        while ((result = this.buildResults.poll()) != null) {
            if (result instanceof ChunkJobResult.Success<? extends ChunkTaskOutput> successfulResult) {
                this.jobMetricsTracker.collectMetrics(successfulResult);
                results.add(successfulResult);
            } else if (result instanceof ChunkJobResult.Failure<? extends ChunkTaskOutput> failure) {
                failure.abort();
            } else {
                throw new AssertionError();
            }
        }

        return results;
    }

    // True if dispatch stopped because the collector's budget ran out while sections remained, i.e. budget-limited rather than work-limited
    private boolean submitRebuildTasks(ChunkJobCollector collector, ChunkUpdateType type) {
        var queue = this.getCurrentRenderListManager().getRebuildLists().byUpdateType().get(type);

        int frame = this.getCurrentRenderListManager().getLastUpdatedFrame();

        while (!queue.isEmpty() && collector.canOffer()) {
            RenderSection section = queue.remove();

            if (section.isDisposed()) {
                continue;
            }

            // The pending type may have changed since queuing: promoted SORT->REBUILD (the REBUILD pass picks it up), cleared by an earlier pass, or nulled after the async BFS (guards against double submission)
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
                var job = this.builder.scheduleTask(task, type.isImportant(), collector::onJobFinished);
                collector.addSubmittedJob(job);

                section.setBuildCancellationToken(job);

                if (!type.isSort()) {
                    // Prevent further sorts from being performed on this section
                    section.setNeedsDynamicTranslucencySorting(false);
                }
            } else {
                var result = new ChunkJobResult.Success<>(new ChunkBuildOutput(section, RenderSection.EMPTY_DATA, Reference2ReferenceMaps.emptyMap(), frame), -1);
                this.buildResults.add(result);

                section.setBuildCancellationToken(null);
            }

            section.setLastSubmittedFrame(frame);
            section.setPendingUpdate(null);
        }

        // The loop only exits early on !canOffer(), so leftover sections mean we ran out of budget, not work.
        return !queue.isEmpty();
    }

    protected abstract @Nullable ChunkBuilderTask<ChunkBuildOutput> createRebuildTask(RenderSection render, int frame);

    // A re-sort for one section at the current camera
    public ChunkBuilderSortTask createSortTask(RenderSection render, int frame) {
        if(!render.isNeedsDynamicTranslucencySorting())
            return null;
        return new ChunkBuilderSortTask(render, (float)cameraPosition.x, (float)cameraPosition.y, (float)cameraPosition.z, frame, render.getTranslucencySortStates());
    }

    // Forces a walk next frame
    public void markGraphDirty() {
        if (this.shadowRenderListManager != null) {
            this.shadowRenderListManager.setNeedsUpdate(true);
        }
        this.renderListManager.setNeedsUpdate(true);
    }

    // Blocks on any async walk, e.g. before teardown
    public void finishAllGraphUpdates() {
        this.renderListManager.finishPreviousGraphUpdate();
        if (this.shadowRenderListManager != null) {
            this.shadowRenderListManager.finishPreviousGraphUpdate();
        }
    }

    // Whether a walk is pending
    public boolean needsUpdate() {
        return this.getCurrentRenderListManager().isNeedsUpdate();
    }

    // The build thread pool
    public ChunkBuilder getBuilder() {
        return this.builder;
    }

    // Stops the builder and frees every region
    public void destroy() {
        this.finishAllGraphUpdates();

        this.builder.shutdown(); // stop all the workers, and cancel any tasks

        for (var result : this.collectChunkBuildResults()) {
            result.output().delete(); // delete resources for any pending tasks (including those that were cancelled)
        }

        this.renderListManager.destroy();
        if (this.shadowRenderListManager != null) {
            this.shadowRenderListManager.destroy();
        }

        try (CommandList commandList = RenderDevice.INSTANCE.createCommandList()) {
            this.regions.delete(commandList);
            this.chunkRenderer.delete(commandList);
        }

        this.renderPassDrawTimers.values().forEach(TimerQueryManager::close);
        this.renderPassDrawTimers.clear();

        this.sectionsWithGlobalEntities.clear();

        this.translucencyTriggerIndex.clear();
        this.coarseTriggeredSections.clear();
    }

    // For the debug screen
    public int getTotalSections() {
        return this.sectionByPosition.size();
    }

    // For the debug screen
    public int getVisibleChunkCount() {
        var sections = 0;
        var iterator = this.getCurrentRenderListManager().getRenderLists().iterator();

        while (iterator.hasNext()) {
            var renderList = iterator.next();
            sections += renderList.getSectionsWithGeometryCount();
        }

        return sections;
    }

    // Queues work for the render thread from another thread
    public final void scheduleAsyncTask(Runnable runnable) {
        if (Thread.currentThread() == this.renderThread) {
            // Run immediately, otherwise the thread may deadlock waiting for itself
            runnable.run();
        } else {
            asyncSubmittedTasks.add(runnable);
        }
    }

    // Marshals a rebuild request onto the render thread
    private void scheduleRebuildOffThread(int x, int y, int z, boolean important) {
        scheduleAsyncTask(() -> this.scheduleSectionForRebuild(x, y, z, important));
    }

    // Entry point from block updates, thread-safe
    public final void scheduleRebuild(int x, int y, int z, boolean important) {
        if (Thread.currentThread() != renderThread) {
            this.scheduleRebuildOffThread(x, y, z, important);
            return;
        }

        this.scheduleSectionForRebuild(x, y, z, important);
    }

    // Platform hook; drops cloned chunk data
    protected void invalidateCachedSectionData(RenderSection section) {

    }

    // Queues a rebuild, promoting the update type if a lesser one was pending
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
                if (!this.getCurrentRenderListManager().isNeedsUpdate() && this.sectionsRequestingUpdate.size() < this.builder.getSchedulingBudget()) {
                    this.sectionsRequestingUpdate.add(section);
                } else {
                    this.markGraphDirty();
                }
            }
        }
    }

    // Every section, e.g. after a resource reload
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

    // Sections near the camera jump the queue
    private boolean shouldPrioritizeRebuild(RenderSection section) {
        return this.lastCameraPosition != null && section.getSquaredDistanceFromBlockCenter(this.lastCameraPosition.x(), this.lastCameraPosition.y(), this.lastCameraPosition.z()) < NEARBY_REBUILD_DISTANCE;
    }

    // True if rebuilds near the player should block the main thread; reduces flickering but can cause lag spikes
    protected boolean allowImportantRebuilds() {
        return false;
    }

    // Render distance clamped to the world's loaded area
    private float getEffectiveRenderDistance() {
        var color = ChunkShaderFogComponent.FOG_SERVICE.getFogColor();
        var alpha = color[3];
        var distance = ChunkShaderFogComponent.FOG_SERVICE.getFogCutoff();

        var renderDistance = this.getRenderDistance();

        // The fog must be fully opaque in order to skip rendering of chunks behind it
        if (Math.abs(alpha - 1.0f) >= 1.0E-5F) {
            return renderDistance;
        }

        return Math.min(renderDistance, distance + 0.5f);
    }

    // Render distance in blocks
    private float getRenderDistance() {
        return this.renderDistance * 16.0f;
    }

    // Section by coordinates, or null
    private RenderSection getRenderSection(int x, int y, int z) {
        return this.sectionByPosition.get(PositionUtil.packSection(x, y, z));
    }

    // Every section
    public Collection<RenderSection> getAllRenderSections() {
        return Collections.unmodifiableCollection(this.sectionByPosition.values());
    }

    // GPU time per pass from the timer queries
    private Object2LongMap<TerrainRenderPass> computeRenderPassTimingsMap() {
        Object2LongOpenHashMap<TerrainRenderPass> map = new Object2LongOpenHashMap<>();
        for (var entry : renderPassDrawTimers.entrySet()) {
            map.put(entry.getKey(), entry.getValue().getLastTime());
        }
        return map;
    }

    protected final Supplier<Object2LongMap<TerrainRenderPass>> renderPassTimingsDebounced = new ExpiringSupplier<>(this::computeRenderPassTimingsMap, 1, TimeUnit.SECONDS);

    // Every line the debug screen shows
    public Collection<String> getDebugStrings() {
        List<String> list = new ArrayList<>();

        int count = 0, indexCount = 0;

        long deviceUsed = 0;
        long deviceAllocated = 0;

        long indexUsed = 0, indexAllocated = 0;

        for (var region : this.regions.getLoadedRegions()) {
            for (var resources : region.getAllResources()) {
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

        list.add(String.format("Chunk Queues: U=%02d (P0=%03d | P1=%03d | P2=%03d)",
                this.buildResults.size(),
                rebuildLists.getUpdateCount(ChunkUpdateType.IMPORTANT_REBUILD),
                rebuildLists.getUpdateCount(ChunkUpdateType.REBUILD),
                rebuildLists.getUpdateCount(ChunkUpdateType.INITIAL_BUILD)
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

        if (renderListManager.getRenderLists().hasSortedPass()) {
            list.add(debugStats.getSortingString());
        }

        return list;
    }

    // The list manager for this frame
    private RenderListManager getCurrentRenderListManager() {
        return isInShadowPass() ? this.shadowRenderListManager : this.renderListManager;
    }

    // The lists to draw
    public SortedRenderLists getRenderLists() {
        return this.getCurrentRenderListManager().getRenderLists();
    }

    // Whether the section has data
    public boolean isSectionBuilt(int x, int y, int z) {
        var section = this.getRenderSection(x, y, z);
        return section != null && section.isBuilt();
    }

    // Column loaded; adds its sections
    public void onChunkAdded(int x, int z) {
        for (int y = this.minSection; y < this.maxSection; y++) {
            this.onSectionAdded(x, y, z);
        }
    }

    // Column unloaded; removes its sections
    public void onChunkRemoved(int x, int z) {
        for (int y = this.minSection; y < this.maxSection; y++) {
            this.onSectionRemoved(x, y, z);
        }
    }

    // Debug switch for one pass
    public void toggleRenderingForTerrainPass(TerrainRenderPass pass) {
        if (!this.disabledRenderPasses.add(pass)) {
            this.disabledRenderPasses.remove(pass);
        }
    }

    // Sections whose block entities render regardless of visibility
    public final Collection<RenderSection> getSectionsWithGlobalEntities() {
        return ReferenceSets.unmodifiable(this.sectionsWithGlobalEntities);
    }

    // From the sprite ticker
    public String getTickerDebugString() {
        return this.getCurrentRenderListManager().getTickerDebugString();
    }
}
