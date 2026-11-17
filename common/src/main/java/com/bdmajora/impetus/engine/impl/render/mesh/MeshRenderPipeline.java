package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshStatistics;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshTranslucencySorting;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import com.bdmajora.impetus.engine.impl.render.chunk.shader.TerrainDynamicLights;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.BindlessBuffer;
import com.bdmajora.impetus.engine.impl.render.mesh.region.MeshRegionStore;
import com.bdmajora.impetus.engine.impl.render.mesh.region.MeshSectionStore;
import com.bdmajora.impetus.engine.impl.render.mesh.region.RegionVisibilityTracker;
import com.bdmajora.impetus.engine.impl.render.mesh.renderers.NvidiumShading;
import com.bdmajora.impetus.engine.impl.render.mesh.renderers.RegionRasterizer;
import com.bdmajora.impetus.engine.impl.render.mesh.renderers.SectionRasterizer;
import com.bdmajora.impetus.engine.impl.render.mesh.renderers.SectionSorter;
import com.bdmajora.impetus.engine.impl.render.mesh.renderers.TerrainRasterizer;
import com.bdmajora.impetus.engine.impl.render.mesh.util.DownloadStream;
import com.bdmajora.impetus.engine.impl.render.mesh.util.QuadArena;
import com.bdmajora.impetus.engine.impl.render.mesh.util.UploadStream;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL43;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.opengl.NVVertexBufferUnifiedMemory;
import org.lwjgl.opengl.NVRepresentativeFragmentTest;
import org.lwjgl.opengl.NVUniformBufferUnifiedMemory;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Draws the terrain with mesh shaders from regions and sections in bindless buffers.
public class MeshRenderPipeline {
    // Bytes of the scene uniform block.
    static final int SCENE_BYTES = align(16 * 4 + 16 + 16 + 16 + 16 + 11 * 8 + 8 + 4 * 5 + 4 * 2 + 2 + 1, 16);

    // Bytes of the statistics block.
    private static final int STATISTICS_BYTES = 16;

    // Settings of the pipeline.
    private final MeshTerrainConfig config;
    // Number of region slots.
    private final int maxRegions;

    // Stream that copies data to the GPU.
    private final UploadStream uploadStream;
    // Stream that reads data back.
    private final DownloadStream downloadStream;
    // Store of the sections.
    private final MeshSectionStore sections;

    // Scene uniform block and visible region list.
    private final BindlessBuffer sceneUniform;
    // Visibility of each region.
    private final BindlessBuffer regionVisibility;
    // Visibility of each section.
    private final BindlessBuffer sectionVisibility;
    // Draw commands of the opaque terrain.
    private final BindlessBuffer terrainCommands;
    // Draw commands of the translucent terrain.
    private final BindlessBuffer translucentCommands;
    // Regions that the sorter orders.
    private final BindlessBuffer sortingRegions;
    // Counters of the GPU.
    private final BindlessBuffer statistics;
    // Scene uniform block of the shadow pass.
    private final BindlessBuffer shadowScene;
    // Draw commands of the shadow pass.
    private final BindlessBuffer shadowCommands;
    // Moving lights of the main pass.
    private final BindlessBuffer sceneLights;
    // Moving lights of the shadow pass.
    private final BindlessBuffer shadowLights;

    // Rasterizer of the region boxes.
    private final RegionRasterizer regionRasterizer;
    // Rasterizer of the section boxes.
    private final SectionRasterizer sectionRasterizer;
    // Programs that shade the terrain.
    private final MeshTerrainShading shading;
    // Sorter of the sections or null.
    private final SectionSorter sectionSorter;

    // History of the region visibility.
    private final RegionVisibilityTracker visibilityTracker;

    // Regions that were inside the frustum last frame.
    private final BitSet regionsInFrustum;
    // Regions that the sorter orders this frame.
    private final BitSet regionsToSort;

    // Sort keys of the visible regions.
    private final IntArrayList visibleRegions = new IntArrayList();
    // Regions inside the shadow frustum.
    private final IntArrayList shadowRegions = new IntArrayList();

    // Scratch matrix.
    private final Matrix4f mvp = new Matrix4f();

    // Number of visible regions of the last frame.
    private int previousRegionCount;
    // Number of regions with translucent sections.
    private int translucentRegionCount;
    // Number of regions of the shadow pass.
    private int shadowRegionCount;
    // Counter of frames.
    private int frameId;

    // Matrices of the frame.
    private @Nullable ChunkRenderMatrices frameMatrices;
    // Matrices of the shadow pass.
    private @Nullable ChunkRenderMatrices shadowMatrices;

    // Counters that the GPU reported.
    private int frustumCount, regionCount, sectionCount, quadCount;

    // Creates a pipeline with the default programs.
    public MeshRenderPipeline(MeshTerrainConfig config, int maxRegions, long uploadBufferSize, long downloadBufferSize, long geometryBudget) {
        this(config, maxRegions, uploadBufferSize, downloadBufferSize, geometryBudget, null);
    }

    // Creates a pipeline and rejects a null config or a region count outside the range of an id.
    public MeshRenderPipeline(MeshTerrainConfig config, int maxRegions, long uploadBufferSize, long downloadBufferSize, long geometryBudget,
                              @Nullable MeshTerrainShading shading) {
        this.config = java.util.Objects.requireNonNull(config, "config");
        if (maxRegions < 1 || maxRegions > 0xFFFF) {
            throw new IllegalArgumentException("Region count must be between 1 and 65535 but was " + maxRegions);
        }
        this.maxRegions = maxRegions;

        List<Runnable> built = new ArrayList<>();
        if (shading != null) {
            built.add(shading::delete);
        }
        try {
            ShaderConstants constants = config.shaderConstants().build();
            this.regionRasterizer = new RegionRasterizer(constants);
            built.add(this.regionRasterizer::delete);
            this.sectionRasterizer = new SectionRasterizer(constants);
            built.add(this.sectionRasterizer::delete);
            this.shading = shading != null ? shading : new NvidiumShading(constants,
                    config.temporalCoherence() ? config.shaderConstants().add("TEMPORAL").build() : null);
            if (shading == null) {
                built.add(this.shading::delete);
            }
            this.sectionSorter = config.sorting() != MeshTranslucencySorting.NONE ? new SectionSorter(constants) : null;
        } catch (RuntimeException e) {
            built.forEach(Runnable::run);
            throw e;
        }

        this.uploadStream = new UploadStream(uploadBufferSize);
        this.downloadStream = new DownloadStream(downloadBufferSize);

        MeshRegionStore regions = new MeshRegionStore(maxRegions, this.uploadStream);
        QuadArena arena = new QuadArena(geometryBudget, this.shading.vertexStride());
        this.sections = new MeshSectionStore(regions, arena, this.uploadStream);

        this.sceneUniform = new BindlessBuffer(SCENE_BYTES + maxRegions * 2L);
        this.regionVisibility = new BindlessBuffer(maxRegions);
        this.sectionVisibility = new BindlessBuffer(maxRegions * (long) MeshRegionStore.SECTIONS_PER_REGION);
        this.terrainCommands = new BindlessBuffer(maxRegions * 8L);
        this.translucentCommands = new BindlessBuffer(maxRegions * 8L);
        this.sortingRegions = new BindlessBuffer(maxRegions * 2L);
        this.statistics = new BindlessBuffer(STATISTICS_BYTES);
        this.shadowScene = new BindlessBuffer(SCENE_BYTES);
        this.shadowCommands = new BindlessBuffer(maxRegions * 8L);
        this.sceneLights = new BindlessBuffer(TerrainDynamicLights.BYTES);
        this.shadowLights = new BindlessBuffer(TerrainDynamicLights.BYTES);

        this.regionVisibility.clear();
        this.sectionVisibility.clear();
        this.terrainCommands.clear();
        this.translucentCommands.clear();
        this.statistics.clear();

        this.regionsInFrustum = new BitSet(maxRegions);
        this.regionsToSort = new BitSet(maxRegions);
        this.visibilityTracker = new RegionVisibilityTracker(maxRegions);

        regions.setUploadListener(this.regionsToSort::set);
    }

    // Returns the section store.
    public MeshSectionStore getSections() {
        return this.sections;
    }

    // Returns the upload stream.
    public UploadStream getUploadStream() {
        return this.uploadStream;
    }

    // Returns the visibility history.
    public RegionVisibilityTracker getVisibilityTracker() {
        return this.visibilityTracker;
    }

    // Draws the opaque terrain of the last frame and computes the next frame.
    public void renderFrame(Viewport viewport, ChunkRenderMatrices matrices, MeshFog fog,
                            double cameraX, double cameraY, double cameraZ,
                            int screenWidth, int screenHeight) {
        this.renderFrame(viewport, matrices, fog, MeshCamera.of(matrices, cameraX, cameraY, cameraZ), screenWidth, screenHeight, null);
    }

    // Draws the opaque terrain with a resolved camera and the state of a pass.
    public void renderFrame(Viewport viewport, ChunkRenderMatrices matrices, MeshFog fog, MeshCamera camera,
                            int screenWidth, int screenHeight, @Nullable TerrainRenderPass pass) {
        MeshRegionStore regions = this.sections.getRegions();
        this.translucentRegionCount = 0;
        this.frameMatrices = matrices;

        if (regions.getRegionCount() == 0) {
            return;
        }

        int visibleCount = collectVisibleRegions(regions, viewport, camera);

        if (visibleCount == 0) {
            this.previousRegionCount = 0;
            return;
        }

        writeSceneUniform(this.sceneUniform, matrices, fog, camera, screenWidth, screenHeight, visibleCount);
        writeLights(this.sceneLights, camera);

        this.sections.commit();
        int sortCount = queueSorts();

        this.uploadStream.commit();

        boolean shaded = this.shading.bindOpaque(MeshDrawPhase.MAIN, pass, matrices);

        bindScene(this.sceneUniform, this.sceneLights);
        LWJGL.glEnable(GL11.GL_DEPTH_TEST);

        if (shaded && this.previousRegionCount != 0) {
            TerrainRasterizer.drawIndirect(this.previousRegionCount, this.terrainCommands.getDeviceAddress());
            LWJGL.glMemoryBarrier(GL42.GL_FRAMEBUFFER_BARRIER_BIT);
        }

        LWJGL.glDepthFunc(GL11.GL_LEQUAL);
        LWJGL.glDepthMask(false);
        LWJGL.glColorMask(false, false, false, false);
        LWJGL.glEnable(NVRepresentativeFragmentTest.GL_REPRESENTATIVE_FRAGMENT_TEST_NV);

        this.regionRasterizer.raster(visibleCount);
        LWJGL.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

        short[] order = regionOrder(visibleCount);
        this.downloadStream.download(this.regionVisibility, 0L, visibleCount,
                address -> this.visibilityTracker.record(order, order.length, address));

        this.sectionRasterizer.raster(visibleCount);
        LWJGL.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

        LWJGL.glDisable(NVRepresentativeFragmentTest.GL_REPRESENTATIVE_FRAGMENT_TEST_NV);
        LWJGL.glDepthMask(true);
        LWJGL.glColorMask(true, true, true, true);

        LWJGL.glMemoryBarrier(GL42.GL_COMMAND_BARRIER_BIT);

        if (shaded && this.config.temporalCoherence() && this.shading.bindOpaque(MeshDrawPhase.TEMPORAL, pass, matrices)) {
            TerrainRasterizer.drawIndirect(visibleCount, this.terrainCommands.getDeviceAddress());
        }

        if (sortCount != 0) {
            this.sectionSorter.dispatch(sortCount);
            LWJGL.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
        }

        this.previousRegionCount = visibleCount;
        this.translucentRegionCount = visibleCount;

        unbindScene();
        this.shading.finish();
    }

    // Draws the translucent terrain.
    public void renderTranslucent() {
        this.renderTranslucent(null);
    }

    // Draws the translucent terrain with the state of a pass.
    public void renderTranslucent(@Nullable TerrainRenderPass pass) {
        if (this.translucentRegionCount != 0) {
            boolean shaded = this.shading.bindTranslucent(MeshDrawPhase.MAIN, pass, this.frameMatrices);
            bindScene(this.sceneUniform, this.sceneLights);
            LWJGL.glEnable(GL11.GL_DEPTH_TEST);
            if (shaded) {
                TerrainRasterizer.drawIndirect(this.translucentRegionCount, this.translucentCommands.getDeviceAddress());
            }
            unbindScene();
            this.shading.finish();

            if (this.config.statistics().includes(MeshStatistics.REGIONS)) {
                this.downloadStream.download(this.statistics, 0L, STATISTICS_BYTES, this::readStatistics);
                MemoryUtil.memSet(this.uploadStream.upload(this.statistics, 0L, STATISTICS_BYTES), 0, STATISTICS_BYTES);
            }
        }
    }

    // Draws the opaque shadow terrain.
    public void renderShadow(Viewport viewport, ChunkRenderMatrices matrices, MeshCamera camera, @Nullable TerrainRenderPass pass,
                             int width, int height) {
        MeshRegionStore regions = this.sections.getRegions();
        this.shadowRegionCount = 0;
        this.shadowMatrices = matrices;

        if (!this.shading.hasShadowPrograms() || regions.getRegionCount() == 0) {
            return;
        }

        this.sections.commit();
        int count = writeShadowCommands(regions, viewport);

        if (count == 0) {
            return;
        }

        writeSceneUniform(this.shadowScene, matrices, MeshFog.NONE, camera, width, height, count);
        writeLights(this.shadowLights, camera);
        this.uploadStream.commit();

        boolean shaded = this.shading.bindOpaque(MeshDrawPhase.SHADOW, pass, matrices);
        bindScene(this.shadowScene, this.shadowLights);
        LWJGL.glEnable(GL11.GL_DEPTH_TEST);
        if (shaded) {
            TerrainRasterizer.drawIndirect(count, this.shadowCommands.getDeviceAddress());
        }
        unbindScene();
        this.shading.finish();

        this.shadowRegionCount = count;
    }

    // Draws the translucent shadow terrain.
    public void renderShadowTranslucent(@Nullable TerrainRenderPass pass) {
        if (this.shadowRegionCount == 0) {
            return;
        }

        boolean shaded = this.shading.bindTranslucent(MeshDrawPhase.SHADOW, pass, this.shadowMatrices);
        bindScene(this.shadowScene, this.shadowLights);
        LWJGL.glEnable(GL11.GL_DEPTH_TEST);
        if (shaded) {
            TerrainRasterizer.drawIndirect(this.shadowRegionCount, this.shadowCommands.getDeviceAddress());
        }
        unbindScene();
        this.shading.finish();
    }

    // Retires the frame by sending the uploads and running the finished readbacks.
    public void endFrame() {
        this.downloadStream.endFrame();
        this.uploadStream.endFrame();
    }

    // Removes a region and its history and ignores a region that does not exist.
    public void evictRegion(int regionId) {
        if (!this.sections.getRegions().regionExists(regionId)) {
            return;
        }
        this.sections.removeRegion(regionId);
        this.visibilityTracker.reset(regionId);
        this.regionsInFrustum.clear(regionId);
        this.regionsToSort.clear(regionId);
    }

    // Adds the statistics line when statistics are on.
    public void addDebugStrings(List<String> lines) {
        MeshStatistics level = this.config.statistics();

        if (level == MeshStatistics.NONE) {
            return;
        }

        StringBuilder line = new StringBuilder("Mesh statistics: F ").append(this.frustumCount);
        if (level.includes(MeshStatistics.REGIONS)) {
            line.append(", R ").append(this.regionCount);
        }
        if (level.includes(MeshStatistics.SECTIONS)) {
            line.append(", S ").append(this.sectionCount);
        }
        if (level.includes(MeshStatistics.QUADS)) {
            line.append(", Q ").append(this.quadCount);
        }
        lines.add(line.toString());
    }

    // Deletes every buffer and program and continues after a failure.
    public void delete() {
        List<Runnable> steps = new ArrayList<>(List.of(
                this.regionRasterizer::delete, this.sectionRasterizer::delete, this.shading::delete,
                this.sceneUniform::delete, this.regionVisibility::delete, this.sectionVisibility::delete,
                this.terrainCommands::delete, this.translucentCommands::delete, this.sortingRegions::delete,
                this.statistics::delete, this.shadowScene::delete, this.shadowCommands::delete,
                this.sceneLights::delete, this.shadowLights::delete,
                this.sections.getArena()::delete, this.sections.getRegions()::delete,
                this.downloadStream::delete, this.uploadStream::delete));

        if (this.sectionSorter != null) {
            steps.add(this.sectionSorter::delete);
        }

        RuntimeException failure = null;

        for (Runnable step : steps) {
            try {
                step.run();
            } catch (RuntimeException e) {
                if (failure == null) failure = e; else failure.addSuppressed(e);
            }
        }

        if (failure != null) {
            throw failure;
        }
    }

    // Collects the regions inside the frustum in front to back order.
    private int collectVisibleRegions(MeshRegionStore regions, Viewport viewport, MeshCamera camera) {
        int cameraSectionX = camera.sectionX();
        int cameraSectionY = camera.sectionY();
        int cameraSectionZ = camera.sectionZ();
        this.visibleRegions.clear();
        boolean evictByDistance = this.config.keepsBeyondRenderDistance() && !this.config.keepsEverything();

        for (int regionId = 0; regionId < regions.getMaxRegionIndex(); regionId++) {
            if (!regions.regionExists(regionId)) {
                continue;
            }

            if (evictByDistance && !regions.isWithinChunks(regionId, this.config.regionKeepDistance() + 4,
                    cameraSectionX, cameraSectionY, cameraSectionZ)) {
                this.evictRegion(regionId);
                continue;
            }

            if (regions.isRegionVisible(viewport, regionId)) {
                int distance = regions.distanceTo(regionId, cameraSectionX, cameraSectionY, cameraSectionZ);
                this.visibleRegions.add((distance << 16) | regionId);
                this.regionsInFrustum.set(regionId);

                if (regions.isRegionInCameraAxis(regionId, camera.eyeX(), camera.eyeY(), camera.eyeZ())) {
                    this.regionsToSort.set(regionId);
                }
            } else if (this.regionsInFrustum.get(regionId)) {
                this.sectionVisibility.clearRange((long) regionId << 8, MeshRegionStore.SECTIONS_PER_REGION);
                this.regionsInFrustum.clear(regionId);
            }
        }

        int count = this.visibleRegions.size();
        this.frustumCount = count;

        if (count == 0) {
            return 0;
        }

        int[] keys = this.visibleRegions.elements();
        IntArrays.quickSort(keys, 0, count);

        long ptr = this.uploadStream.upload(this.sceneUniform, SCENE_BYTES, count * 2L);

        for (int index = 0; index < count; index++) {
            MemoryUtil.memPutShort(ptr + ((long) index << 1), (short) (keys[index] & 0xFFFF));
        }

        return count;
    }

    // Returns the visible region ids in list order.
    private short[] regionOrder(int count) {
        short[] order = new short[count];
        int[] keys = this.visibleRegions.elements();

        for (int index = 0; index < count; index++) {
            order[index] = (short) (keys[index] & 0xFFFF);
        }

        return order;
    }

    // Uploads the regions for the sorter and returns their number.
    private int queueSorts() {
        int count = this.regionsToSort.cardinality();

        if (this.sectionSorter == null || count == 0) {
            this.regionsToSort.clear();
            return 0;
        }

        long ptr = this.uploadStream.upload(this.sortingRegions, 0L, count * 2L);
        int index = 0;

        for (int regionId = this.regionsToSort.nextSetBit(0); regionId >= 0; regionId = this.regionsToSort.nextSetBit(regionId + 1)) {
            MemoryUtil.memPutShort(ptr + ((long) index++ << 1), (short) regionId);
        }

        this.regionsToSort.clear();
        return count;
    }

    // Binds a scene block and its moving lights by address.
    private static void bindScene(BindlessBuffer scene, BindlessBuffer lights) {
        LWJGL.glEnableClientState(NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_UNIFIED_NV);
        LWJGL.glEnableClientState(NVVertexBufferUnifiedMemory.GL_VERTEX_ATTRIB_ARRAY_UNIFIED_NV);
        LWJGL.glEnableClientState(NVVertexBufferUnifiedMemory.GL_ELEMENT_ARRAY_UNIFIED_NV);
        LWJGL.glEnableClientState(MeshShaderSupport.GL_DRAW_INDIRECT_UNIFIED_NV);
        LWJGL.glBufferAddressRangeNV(NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_ADDRESS_NV, 0, scene.getDeviceAddress(), SCENE_BYTES);
        // Points the light block binding at its buffer address.
        LWJGL.glBufferAddressRangeNV(NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_ADDRESS_NV, TerrainDynamicLights.BINDING,
                lights.getDeviceAddress(), TerrainDynamicLights.BYTES);
    }

    // Writes the moving lights relative to the render origin.
    private void writeLights(BindlessBuffer target, MeshCamera camera) {
        long ptr = this.uploadStream.upload(target, 0, TerrainDynamicLights.BYTES);
        TerrainDynamicLights.write(ptr, camera.renderX(), camera.renderY(), camera.renderZ());
    }

    // Unbinds the scene block and the program.
    private static void unbindScene() {
        LWJGL.glDisableClientState(NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_UNIFIED_NV);
        LWJGL.glDisableClientState(NVVertexBufferUnifiedMemory.GL_VERTEX_ATTRIB_ARRAY_UNIFIED_NV);
        LWJGL.glDisableClientState(NVVertexBufferUnifiedMemory.GL_ELEMENT_ARRAY_UNIFIED_NV);
        LWJGL.glDisableClientState(MeshShaderSupport.GL_DRAW_INDIRECT_UNIFIED_NV);
        LWJGL.glUseProgram(0);
    }

    // Writes one command per region in the shadow frustum and returns the number.
    private int writeShadowCommands(MeshRegionStore regions, Viewport viewport) {
        this.shadowRegions.clear();

        for (int regionId = 0; regionId < regions.getMaxRegionIndex(); regionId++) {
            if (regions.regionExists(regionId) && regions.isRegionVisible(viewport, regionId)) {
                this.shadowRegions.add(regionId);
            }
        }

        int count = this.shadowRegions.size();

        if (count != 0) {
            long ptr = this.uploadStream.upload(this.shadowCommands, 0L, count * 8L);

            for (int index = 0; index < count; index++) {
                int regionId = this.shadowRegions.getInt(index);
                MemoryUtil.memPutInt(ptr + index * 8L, regions.getSectionCount(regionId));
                MemoryUtil.memPutInt(ptr + index * 8L + 4, regionId << 8);
            }
        }

        return count;
    }

    // Reads the counters of the GPU.
    private void readStatistics(long address) {
        this.regionCount = MemoryUtil.memGetInt(address);
        this.sectionCount = MemoryUtil.memGetInt(address + 4);
        this.quadCount = MemoryUtil.memGetInt(address + 8);
    }

    // Writes the scene block.
    private void writeSceneUniform(BindlessBuffer target, ChunkRenderMatrices matrices, MeshFog fog, MeshCamera camera,
                                   int screenWidth, int screenHeight, int visibleCount) {
        int sectionX = camera.sectionX();
        int sectionY = camera.sectionY();
        int sectionZ = camera.sectionZ();

        float renderX = (float) ((sectionX << 4) - camera.renderX());
        float renderY = (float) ((sectionY << 4) - camera.renderY());
        float renderZ = (float) ((sectionZ << 4) - camera.renderZ());

        long ptr = this.uploadStream.upload(target, 0, SCENE_BYTES);

        this.mvp.set(matrices.projection())
                .mul(matrices.modelView())
                .translate(renderX, renderY, renderZ)
                .getToAddress(ptr);
        ptr += 16 * 4;

        MemoryUtil.memPutInt(ptr, sectionX);
        MemoryUtil.memPutInt(ptr + 4, sectionY);
        MemoryUtil.memPutInt(ptr + 8, sectionZ);
        MemoryUtil.memPutInt(ptr + 12, 0);
        ptr += 16;

        ptr = putVector(ptr, (float) ((sectionX << 4) - camera.eyeX()), (float) ((sectionY << 4) - camera.eyeY()), (float) ((sectionZ << 4) - camera.eyeZ()));
        ptr = putVector(ptr, renderX, renderY, renderZ);

        float[] colour = fog.colour();
        for (int i = 0; i < 4; i++) {
            MemoryUtil.memPutFloat(ptr + i * 4L, colour[i]);
        }
        ptr += 16;

        MeshRegionStore regions = this.sections.getRegions();

        ptr = putPointer(ptr, this.sceneUniform.getDeviceAddress() + SCENE_BYTES);
        ptr = putPointer(ptr, regions.getRegionBufferAddress());
        ptr = putPointer(ptr, regions.getSectionBufferAddress());
        ptr = putPointer(ptr, this.regionVisibility.getDeviceAddress());
        ptr = putPointer(ptr, this.sectionVisibility.getDeviceAddress());
        ptr = putPointer(ptr, this.terrainCommands.getDeviceAddress());
        ptr = putPointer(ptr, this.translucentCommands.getDeviceAddress());
        ptr = putPointer(ptr, this.sortingRegions.getDeviceAddress());
        ptr = putPointer(ptr, this.sections.getArena().getBuffer().getDeviceAddress());
        ptr = putPointer(ptr, this.statistics.getDeviceAddress());
        ptr = putPointer(ptr, regions.getSectionTimeBufferAddress());

        MemoryUtil.memPutFloat(ptr, screenWidth / 2.0f);
        MemoryUtil.memPutFloat(ptr + 4, screenHeight / 2.0f);
        ptr += 8;

        MemoryUtil.memPutFloat(ptr, fog.start());
        MemoryUtil.memPutFloat(ptr + 4, fog.end());
        MemoryUtil.memPutFloat(ptr + 8, fog.density());
        MemoryUtil.memPutInt(ptr + 12, fog.mode());
        MemoryUtil.memPutInt(ptr + 16, fog.shape());
        ptr += 20;

        int fadeMs = ImpetusRuntimeOptions.chunkFadeInDuration;
        MemoryUtil.memPutInt(ptr, regions.currentTimeMs());
        MemoryUtil.memPutFloat(ptr + 4, fadeMs > 0 ? 1.0f / fadeMs : 0.0f);
        ptr += 8;

        MemoryUtil.memPutShort(ptr, (short) visibleCount);
        MemoryUtil.memPutByte(ptr + 2, (byte) (target == this.sceneUniform ? this.frameId++ : this.frameId));
    }

    // Writes a vector and returns the next address.
    private static long putVector(long ptr, float x, float y, float z) {
        MemoryUtil.memPutFloat(ptr, x);
        MemoryUtil.memPutFloat(ptr + 4, y);
        MemoryUtil.memPutFloat(ptr + 8, z);
        MemoryUtil.memPutFloat(ptr + 12, 0.0f);
        return ptr + 16;
    }

    // Writes a pointer and returns the next address.
    private static long putPointer(long ptr, long address) {
        MemoryUtil.memPutLong(ptr, address);
        return ptr + 8;
    }

    // Returns a value rounded up to an alignment.
    static int align(int value, int alignment) {
        int remainder = value % alignment;
        return remainder == 0 ? value : value + (alignment - remainder);
    }

    // Returns the number of region slots.
    public int getMaxRegions() {
        return this.maxRegions;
    }
}
