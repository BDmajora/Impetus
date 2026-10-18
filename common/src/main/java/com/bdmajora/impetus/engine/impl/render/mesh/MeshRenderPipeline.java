package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.ImpetusRuntimeOptions;
import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshStatistics;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshTranslucencySorting;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
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

// Mesh-shader terrain (Nvidium's pipeline): regions and sections live in bindless buffers, region and section boxes are rasterized against depth to find what is visible, and the GPU writes the indirect commands the terrain draws consume; one CPU draw per phase per frame
public class MeshRenderPipeline {
    // Scene uniform block, std140, field for field as scene.glsl declares it
    static final int SCENE_BYTES = align(
            16 * 4          // mat4 MVP
                    + 16    // ivec4 cameraChunk
                    + 16    // vec4 subChunkOffset
                    + 16    // vec4 renderOffset
                    + 16    // vec4 fogColour
                    + 11 * 8 // eleven 64-bit device pointers
                    + 8     // vec2 screenSize
                    + 4 * 5 // fog start, end, density, mode, shape
                    + 4 * 2 // uint timeMs, float chunkFadeInv
                    + 2     // uint16 regionCount
                    + 1,    // uint8 frameId
            16);

    // Regions, sections, quads, and one spare to keep the block 16 bytes
    private static final int STATISTICS_BYTES = 16;

    private final MeshTerrainConfig config;
    private final int maxRegions;

    private final UploadStream uploadStream;
    private final DownloadStream downloadStream;
    private final MeshSectionStore sections;

    private final BindlessBuffer sceneUniform;
    private final BindlessBuffer regionVisibility;
    private final BindlessBuffer sectionVisibility;
    private final BindlessBuffer terrainCommands;
    private final BindlessBuffer translucentCommands;
    private final BindlessBuffer sortingRegions;
    private final BindlessBuffer statistics;
    // The shadow pass's own scene block and the per-region commands the CPU writes for it, since the GPU occlusion phases only know the player's view
    private final BindlessBuffer shadowScene;
    private final BindlessBuffer shadowCommands;

    private final RegionRasterizer regionRasterizer;
    private final SectionRasterizer sectionRasterizer;
    private final MeshTerrainShading shading;
    private final SectionSorter sectionSorter;

    private final RegionVisibilityTracker visibilityTracker;

    // Regions inside the frustum last frame, so one leaving it has its stale section visibility cleared instead of staying "visible" forever
    private final BitSet regionsInFrustum;
    // Regions whose section order the sorter recomputes this frame
    private final BitSet regionsToSort;

    // Sort keys (distance << 16) | regionId, so one sort gives front-to-back order
    private final IntArrayList visibleRegions = new IntArrayList();
    // Region ids the shadow frustum touched this frame
    private final IntArrayList shadowRegions = new IntArrayList();

    private final Matrix4f mvp = new Matrix4f();

    private int previousRegionCount;
    private int translucentRegionCount;
    private int shadowRegionCount;
    private int frameId;

    // What the frame's opaque and shadow draws were bound with, reused by the translucent draws that follow them
    private @Nullable ChunkRenderMatrices frameMatrices;
    private @Nullable ChunkRenderMatrices shadowMatrices;

    // Last values the GPU counters reported; frustum is counted on the CPU
    private int frustumCount, regionCount, sectionCount, quadCount;

    public MeshRenderPipeline(MeshTerrainConfig config, int maxRegions, long uploadBufferSize, long downloadBufferSize, long geometryBudget) {
        this(config, maxRegions, uploadBufferSize, downloadBufferSize, geometryBudget, null);
    }

    // A null shading builds Nvidium's own programs; a shader pack hands in its translated ones, which the pipeline then owns
    public MeshRenderPipeline(MeshTerrainConfig config, int maxRegions, long uploadBufferSize, long downloadBufferSize, long geometryBudget,
                              @Nullable MeshTerrainShading shading) {
        this.config = config;
        this.maxRegions = maxRegions;

        // Programs first: a driver rejecting one throws before any buffer exists, and the ones already built are freed
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

        // The visible region id list sits right after the scene struct in the same allocation, reached by pointer arithmetic off the block's address
        this.sceneUniform = new BindlessBuffer(SCENE_BYTES + maxRegions * 2L);
        this.regionVisibility = new BindlessBuffer(maxRegions);
        this.sectionVisibility = new BindlessBuffer(maxRegions * (long) MeshRegionStore.SECTIONS_PER_REGION);
        this.terrainCommands = new BindlessBuffer(maxRegions * 8L);
        this.translucentCommands = new BindlessBuffer(maxRegions * 8L);
        this.sortingRegions = new BindlessBuffer(maxRegions * 2L);
        this.statistics = new BindlessBuffer(STATISTICS_BYTES);
        this.shadowScene = new BindlessBuffer(SCENE_BYTES);
        this.shadowCommands = new BindlessBuffer(maxRegions * 8L);

        this.regionVisibility.clear();
        this.sectionVisibility.clear();
        this.terrainCommands.clear();
        this.translucentCommands.clear();
        this.statistics.clear();

        this.regionsInFrustum = new BitSet(maxRegions);
        this.regionsToSort = new BitSet(maxRegions);
        this.visibilityTracker = new RegionVisibilityTracker(maxRegions);

        // A re-uploaded region's headers carry the identity draw order again, so it goes back in the sorting queue
        regions.setUploadListener(this.regionsToSort::set);
    }

    // Section geometry and metadata
    public MeshSectionStore getSections() {
        return this.sections;
    }

    // The staging ring uploads go through
    public UploadStream getUploadStream() {
        return this.uploadStream;
    }

    // Region history used to choose what to evict
    public RegionVisibilityTracker getVisibilityTracker() {
        return this.visibilityTracker;
    }

    // Draws the opaque terrain from last frame's commands, then computes this frame's visibility, commands and translucent order; the camera is the exact world position the matrices are relative to
    public void renderFrame(Viewport viewport, ChunkRenderMatrices matrices, MeshFog fog,
                            double cameraX, double cameraY, double cameraZ,
                            int screenWidth, int screenHeight) {
        this.renderFrame(viewport, matrices, fog, MeshCamera.of(matrices, cameraX, cameraY, cameraZ), screenWidth, screenHeight, null);
    }

    // The same with the eye already resolved, and the pass whose state a shader pack's programs apply
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
            // Nothing drew commands this frame, so there are none to replay next frame either
            this.previousRegionCount = 0;
            return;
        }

        writeSceneUniform(this.sceneUniform, matrices, fog, camera, screenWidth, screenHeight, visibleCount);

        // Header uploads first, so a region they reset to the identity draw order is sorted this same frame
        this.sections.commit();
        int sortCount = queueSorts();

        // Everything the shaders read has to be resident before the first draw
        this.uploadStream.commit();

        // A pack's state (its gbuffer above all) goes up before any unified-memory client state is live, since applying it may blit, and stays while the occlusion boxes test against that same depth
        boolean shaded = this.shading.bindOpaque(MeshDrawPhase.MAIN, pass, matrices);

        bindScene(this.sceneUniform);
        LWJGL.glEnable(GL11.GL_DEPTH_TEST);

        // The terrain draw, from last frame's commands
        if (shaded && this.previousRegionCount != 0) {
            TerrainRasterizer.drawIndirect(this.previousRegionCount, this.terrainCommands.getDeviceAddress());
            LWJGL.glMemoryBarrier(GL42.GL_FRAMEBUFFER_BARRIER_BIT);
        }

        // Visibility for the next frame; depth writes stay off because the representative fragment test needs that and occlusion boxes must not pollute the terrain's depth
        LWJGL.glDepthFunc(GL11.GL_LEQUAL);
        LWJGL.glDepthMask(false);
        LWJGL.glColorMask(false, false, false, false);
        LWJGL.glEnable(NVRepresentativeFragmentTest.GL_REPRESENTATIVE_FRAGMENT_TEST_NV);

        this.regionRasterizer.raster(visibleCount);
        LWJGL.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

        // Which visible regions passed the depth test, a few frames late, for picking eviction victims
        short[] order = regionOrder(visibleCount);
        this.downloadStream.download(this.regionVisibility, 0L, visibleCount,
                address -> this.visibilityTracker.record(order, order.length, address));

        this.sectionRasterizer.raster(visibleCount);
        LWJGL.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

        LWJGL.glDisable(NVRepresentativeFragmentTest.GL_REPRESENTATIVE_FRAGMENT_TEST_NV);
        LWJGL.glDepthMask(true);
        LWJGL.glColorMask(true, true, true, true);

        // The commands just written are next frame's terrain draw; drawing the newly visible ones now hides the one-frame lag while turning
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

    // Draws this frame's translucent sections farthest region first, under the blend state the caller set up
    public void renderTranslucent() {
        this.renderTranslucent(null);
    }

    // The same, for a pass whose state a shader pack's water program applies
    public void renderTranslucent(@Nullable TerrainRenderPass pass) {
        if (this.translucentRegionCount != 0) {
            boolean shaded = this.shading.bindTranslucent(MeshDrawPhase.MAIN, pass, this.frameMatrices);
            bindScene(this.sceneUniform);
            LWJGL.glEnable(GL11.GL_DEPTH_TEST);
            if (shaded) {
                TerrainRasterizer.drawIndirect(this.translucentRegionCount, this.translucentCommands.getDeviceAddress());
            }
            unbindScene();
            this.shading.finish();

            if (this.config.statistics().includes(MeshStatistics.REGIONS)) {
                this.downloadStream.download(this.statistics, 0L, STATISTICS_BYTES, this::readStatistics);
                // NVIDIA does not honour a clear of this buffer in the same frame reliably, so the reset goes through the upload stream like Nvidium's
                MemoryUtil.memSet(this.uploadStream.upload(this.statistics, 0L, STATISTICS_BYTES), 0, STATISTICS_BYTES);
            }
        }
    }

    // The shadow map's opaque terrain: every region in the shadow frustum, its sections frustum-tested on the GPU and never occlusion-culled, since the boxes only know the player's depth; nothing when the shading has no shadow programs
    public void renderShadow(Viewport viewport, ChunkRenderMatrices matrices, MeshCamera camera, @Nullable TerrainRenderPass pass,
                             int width, int height) {
        MeshRegionStore regions = this.sections.getRegions();
        this.shadowRegionCount = 0;
        this.shadowMatrices = matrices;

        if (!this.shading.hasShadowPrograms() || regions.getRegionCount() == 0) {
            return;
        }

        // Headers first: a region allocated since the last frame has to exist on the GPU before its commands do
        this.sections.commit();
        int count = writeShadowCommands(regions, viewport);

        if (count == 0) {
            return;
        }

        writeSceneUniform(this.shadowScene, matrices, MeshFog.NONE, camera, width, height, count);
        this.uploadStream.commit();

        boolean shaded = this.shading.bindOpaque(MeshDrawPhase.SHADOW, pass, matrices);
        bindScene(this.shadowScene);
        LWJGL.glEnable(GL11.GL_DEPTH_TEST);
        if (shaded) {
            TerrainRasterizer.drawIndirect(count, this.shadowCommands.getDeviceAddress());
        }
        unbindScene();
        this.shading.finish();

        this.shadowRegionCount = count;
    }

    // The shadow map's translucent terrain over the same regions, after the caller copied the opaque depth aside
    public void renderShadowTranslucent(@Nullable TerrainRenderPass pass) {
        if (this.shadowRegionCount == 0) {
            return;
        }

        boolean shaded = this.shading.bindTranslucent(MeshDrawPhase.SHADOW, pass, this.shadowMatrices);
        bindScene(this.shadowScene);
        LWJGL.glEnable(GL11.GL_DEPTH_TEST);
        if (shaded) {
            TerrainRasterizer.drawIndirect(this.shadowRegionCount, this.shadowCommands.getDeviceAddress());
        }
        unbindScene();
        this.shading.finish();
    }

    // Retires the frame: pending uploads go out and are fenced, and finished readbacks reach their callbacks
    public void endFrame() {
        this.downloadStream.endFrame();
        this.uploadStream.endFrame();
    }

    // Removes every section of a region and forgets its history
    public void evictRegion(int regionId) {
        this.sections.removeRegion(regionId);
        this.visibilityTracker.reset(regionId);
        this.regionsInFrustum.clear(regionId);
        this.regionsToSort.clear(regionId);
    }

    // The F3 statistics line, or nothing when statistics are off
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

    // Frees every buffer and program
    public void delete() {
        this.regionRasterizer.delete();
        this.sectionRasterizer.delete();
        this.shading.delete();
        if (this.sectionSorter != null) {
            this.sectionSorter.delete();
        }

        this.sceneUniform.delete();
        this.regionVisibility.delete();
        this.sectionVisibility.delete();
        this.terrainCommands.delete();
        this.translucentCommands.delete();
        this.sortingRegions.delete();
        this.statistics.delete();
        this.shadowScene.delete();
        this.shadowCommands.delete();

        this.sections.getArena().delete();
        this.sections.getRegions().delete();
        this.downloadStream.delete();
        this.uploadStream.delete();
    }

    // Frustum-culls regions, evicts the ones past the keep distance, sorts front-to-back and writes the id list the shaders index by workgroup
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

            // Four chunks of slack so a region straddling the edge is not dropped and rebuilt as the camera wobbles
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
                // Just left the frustum: its sections were not tested this frame and their stale "visible" bytes would resurrect it the moment it returns
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

    // This frame's visible region ids in list order, kept for the visibility readback that arrives later
    private short[] regionOrder(int count) {
        short[] order = new short[count];
        int[] keys = this.visibleRegions.elements();

        for (int index = 0; index < count; index++) {
            order[index] = (short) (keys[index] & 0xFFFF);
        }

        return order;
    }

    // Uploads the queued regions for the sorter; zero when sorting is off or nothing is queued
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

    // Address-based binding stays live across program changes, unlike a bound UBO, so it is set once per phase group
    private static void bindScene(BindlessBuffer scene) {
        LWJGL.glEnableClientState(NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_UNIFIED_NV);
        LWJGL.glEnableClientState(NVVertexBufferUnifiedMemory.GL_VERTEX_ATTRIB_ARRAY_UNIFIED_NV);
        LWJGL.glEnableClientState(NVVertexBufferUnifiedMemory.GL_ELEMENT_ARRAY_UNIFIED_NV);
        LWJGL.glEnableClientState(MeshShaderSupport.GL_DRAW_INDIRECT_UNIFIED_NV);
        LWJGL.glBufferAddressRangeNV(NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_ADDRESS_NV, 0, scene.getDeviceAddress(), SCENE_BYTES);
    }

    // Turns the unified-memory client states back off and unbinds the last program, so vanilla's fixed-function draws after terrain are unaffected
    private static void unbindScene() {
        LWJGL.glDisableClientState(NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_UNIFIED_NV);
        LWJGL.glDisableClientState(NVVertexBufferUnifiedMemory.GL_VERTEX_ATTRIB_ARRAY_UNIFIED_NV);
        LWJGL.glDisableClientState(NVVertexBufferUnifiedMemory.GL_ELEMENT_ARRAY_UNIFIED_NV);
        LWJGL.glDisableClientState(MeshShaderSupport.GL_DRAW_INDIRECT_UNIFIED_NV);
        LWJGL.glUseProgram(0);
    }

    // One indirect command per region the shadow frustum touches, covering every live section slot; returns how many
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

    // Unpacks the GPU counters a download delivered
    private void readStatistics(long address) {
        this.regionCount = MemoryUtil.memGetInt(address);
        this.sectionCount = MemoryUtil.memGetInt(address + 4);
        this.quadCount = MemoryUtil.memGetInt(address + 8);
    }

    // Fills a scene block, scene.glsl's layout field for field
    private void writeSceneUniform(BindlessBuffer target, ChunkRenderMatrices matrices, MeshFog fog, MeshCamera camera,
                                   int screenWidth, int screenHeight, int visibleCount) {
        int sectionX = camera.sectionX();
        int sectionY = camera.sectionY();
        int sectionZ = camera.sectionZ();

        // Geometry is relative to the eye's section, so the matrix carries that section's offset from the model-view origin and the shader only adds small section deltas, which keeps big numbers out of it
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

        // The pointer table, in scene.glsl's order
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

        // Half-extents, because the mesh shader maps clip space to pixels with one multiply per vertex
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

        // Only the main frame advances the frame id, whose parity alternates the translucent pair swaps; the shadow scene would otherwise pin it
        MemoryUtil.memPutShort(ptr, (short) visibleCount);
        MemoryUtil.memPutByte(ptr + 2, (byte) (target == this.sceneUniform ? this.frameId++ : this.frameId));
    }

    // Writes an xyz vec4 with w zero and advances
    private static long putVector(long ptr, float x, float y, float z) {
        MemoryUtil.memPutFloat(ptr, x);
        MemoryUtil.memPutFloat(ptr + 4, y);
        MemoryUtil.memPutFloat(ptr + 8, z);
        MemoryUtil.memPutFloat(ptr + 12, 0.0f);
        return ptr + 16;
    }

    // Writes a 64-bit address and advances
    private static long putPointer(long ptr, long address) {
        MemoryUtil.memPutLong(ptr, address);
        return ptr + 8;
    }

    // Rounds up to a multiple
    static int align(int value, int alignment) {
        int remainder = value % alignment;
        return remainder == 0 ? value : value + (alignment - remainder);
    }

    // Capacity the per-region tables were sized for
    public int getMaxRegions() {
        return this.maxRegions;
    }
}
