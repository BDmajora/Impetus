package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.gl.shader.ShaderConstants;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshStatistics;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.MeshTranslucencySorting;
import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.BindlessBuffer;
import com.bdmajora.impetus.engine.impl.render.mesh.region.MeshRegionStore;
import com.bdmajora.impetus.engine.impl.render.mesh.region.MeshSectionStore;
import com.bdmajora.impetus.engine.impl.render.mesh.region.RegionVisibilityTracker;
import com.bdmajora.impetus.engine.impl.render.mesh.renderers.RegionRasterizer;
import com.bdmajora.impetus.engine.impl.render.mesh.renderers.SectionRasterizer;
import com.bdmajora.impetus.engine.impl.render.mesh.renderers.SectionSorter;
import com.bdmajora.impetus.engine.impl.render.mesh.renderers.TerrainRasterizer;
import com.bdmajora.impetus.engine.impl.render.mesh.renderers.TranslucentRasterizer;
import com.bdmajora.impetus.engine.impl.render.mesh.util.DownloadStream;
import com.bdmajora.impetus.engine.impl.render.mesh.util.QuadArena;
import com.bdmajora.impetus.engine.impl.render.mesh.util.UploadStream;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
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
                    + 16    // vec4 fogColour
                    + 10 * 8 // ten 64-bit device pointers
                    + 8     // vec2 screenSize
                    + 4 * 5 // fog start, end, density, mode, shape
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

    private final RegionRasterizer regionRasterizer;
    private final SectionRasterizer sectionRasterizer;
    private final TerrainRasterizer terrainRasterizer;
    private final TerrainRasterizer temporalRasterizer;
    private final TranslucentRasterizer translucentRasterizer;
    private final SectionSorter sectionSorter;

    private final RegionVisibilityTracker visibilityTracker;

    // Regions inside the frustum last frame, so one leaving it has its stale section visibility cleared instead of staying "visible" forever
    private final BitSet regionsInFrustum;
    // Regions whose section order the sorter recomputes this frame
    private final BitSet regionsToSort;

    // Sort keys (distance << 16) | regionId, so one sort gives front-to-back order
    private final IntArrayList visibleRegions = new IntArrayList();

    private final Matrix4f mvp = new Matrix4f();

    private int previousRegionCount;
    private int translucentRegionCount;
    private int frameId;

    // Last values the GPU counters reported; frustum is counted on the CPU
    private int frustumCount, regionCount, sectionCount, quadCount;

    public MeshRenderPipeline(MeshTerrainConfig config, int maxRegions, long uploadBufferSize, long downloadBufferSize, long geometryBudget) {
        this.config = config;
        this.maxRegions = maxRegions;

        // Programs first: a driver rejecting one throws before any buffer exists, and the ones already built are freed
        List<Runnable> built = new ArrayList<>();
        try {
            ShaderConstants constants = config.shaderConstants().build();
            this.regionRasterizer = new RegionRasterizer(constants);
            built.add(this.regionRasterizer::delete);
            this.sectionRasterizer = new SectionRasterizer(constants);
            built.add(this.sectionRasterizer::delete);
            this.terrainRasterizer = new TerrainRasterizer("mesh/terrain", constants);
            built.add(this.terrainRasterizer::delete);
            this.temporalRasterizer = config.temporalCoherence()
                    ? new TerrainRasterizer("mesh/terrain_temporal", config.shaderConstants().add("TEMPORAL").build())
                    : null;
            if (this.temporalRasterizer != null) {
                built.add(this.temporalRasterizer::delete);
            }
            this.translucentRasterizer = new TranslucentRasterizer(constants);
            built.add(this.translucentRasterizer::delete);
            this.sectionSorter = config.sorting() != MeshTranslucencySorting.NONE ? new SectionSorter(constants) : null;
        } catch (RuntimeException e) {
            built.forEach(Runnable::run);
            throw e;
        }

        this.uploadStream = new UploadStream(uploadBufferSize);
        this.downloadStream = new DownloadStream(downloadBufferSize);

        MeshRegionStore regions = new MeshRegionStore(maxRegions, this.uploadStream);
        QuadArena arena = new QuadArena(geometryBudget, MeshChunkVertex.STRIDE);
        this.sections = new MeshSectionStore(regions, arena, this.uploadStream);

        // The visible region id list sits right after the scene struct in the same allocation, reached by pointer arithmetic off the block's address
        this.sceneUniform = new BindlessBuffer(SCENE_BYTES + maxRegions * 2L);
        this.regionVisibility = new BindlessBuffer(maxRegions);
        this.sectionVisibility = new BindlessBuffer(maxRegions * (long) MeshRegionStore.SECTIONS_PER_REGION);
        this.terrainCommands = new BindlessBuffer(maxRegions * 8L);
        this.translucentCommands = new BindlessBuffer(maxRegions * 8L);
        this.sortingRegions = new BindlessBuffer(maxRegions * 2L);
        this.statistics = new BindlessBuffer(STATISTICS_BYTES);

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

    // Draws the opaque terrain from last frame's commands, then computes this frame's visibility, commands and translucent order; camera is the exact world position
    public void renderFrame(Viewport viewport, ChunkRenderMatrices matrices, MeshFog fog,
                            double cameraX, double cameraY, double cameraZ,
                            int screenWidth, int screenHeight) {
        MeshRegionStore regions = this.sections.getRegions();
        this.translucentRegionCount = 0;

        if (regions.getRegionCount() == 0) {
            return;
        }

        int cameraSectionX = (int) Math.floor(cameraX) >> 4;
        int cameraSectionY = (int) Math.floor(cameraY) >> 4;
        int cameraSectionZ = (int) Math.floor(cameraZ) >> 4;

        int visibleCount = collectVisibleRegions(regions, viewport, cameraX, cameraY, cameraZ,
                cameraSectionX, cameraSectionY, cameraSectionZ);

        if (visibleCount == 0) {
            // Nothing drew commands this frame, so there are none to replay next frame either
            this.previousRegionCount = 0;
            return;
        }

        writeSceneUniform(matrices, fog, cameraX, cameraY, cameraZ,
                cameraSectionX, cameraSectionY, cameraSectionZ, screenWidth, screenHeight, visibleCount);

        // Header uploads first, so a region they reset to the identity draw order is sorted this same frame
        this.sections.commit();
        int sortCount = queueSorts();

        // Everything the shaders read has to be resident before the first draw
        this.uploadStream.commit();

        bindScene();

        // The terrain draw, from last frame's commands
        if (this.previousRegionCount != 0) {
            LWJGL.glEnable(GL11.GL_DEPTH_TEST);
            this.terrainRasterizer.raster(this.previousRegionCount, this.terrainCommands.getDeviceAddress());
            LWJGL.glMemoryBarrier(GL42.GL_FRAMEBUFFER_BARRIER_BIT);
        }

        // Visibility for the next frame; depth writes stay off because the representative fragment test needs that and occlusion boxes must not pollute the terrain's depth
        LWJGL.glEnable(GL11.GL_DEPTH_TEST);
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

        if (this.temporalRasterizer != null) {
            this.temporalRasterizer.raster(visibleCount, this.terrainCommands.getDeviceAddress());
        }

        if (sortCount != 0) {
            this.sectionSorter.dispatch(sortCount);
            LWJGL.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);
        }

        this.previousRegionCount = visibleCount;
        this.translucentRegionCount = visibleCount;

        unbindScene();
        LWJGL.glDepthFunc(GL11.GL_LEQUAL);
        LWJGL.glDisable(GL11.GL_DEPTH_TEST);
    }

    // Draws this frame's translucent sections farthest region first, under the blend state the caller set up
    public void renderTranslucent() {
        if (this.translucentRegionCount != 0) {
            bindScene();
            LWJGL.glEnable(GL11.GL_DEPTH_TEST);
            this.translucentRasterizer.raster(this.translucentRegionCount, this.translucentCommands.getDeviceAddress());
            LWJGL.glDisable(GL11.GL_DEPTH_TEST);
            unbindScene();

            if (this.config.statistics().includes(MeshStatistics.REGIONS)) {
                this.downloadStream.download(this.statistics, 0L, STATISTICS_BYTES, this::readStatistics);
                // NVIDIA does not honour a clear of this buffer in the same frame reliably, so the reset goes through the upload stream like Nvidium's
                MemoryUtil.memSet(this.uploadStream.upload(this.statistics, 0L, STATISTICS_BYTES), 0, STATISTICS_BYTES);
            }
        }
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
        this.terrainRasterizer.delete();
        if (this.temporalRasterizer != null) {
            this.temporalRasterizer.delete();
        }
        this.translucentRasterizer.delete();
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

        this.sections.getArena().delete();
        this.sections.getRegions().delete();
        this.downloadStream.delete();
        this.uploadStream.delete();
    }

    // Frustum-culls regions, evicts the ones past the keep distance, sorts front-to-back and writes the id list the shaders index by workgroup
    private int collectVisibleRegions(MeshRegionStore regions, Viewport viewport, double cameraX, double cameraY, double cameraZ,
                                      int cameraSectionX, int cameraSectionY, int cameraSectionZ) {
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

                if (regions.isRegionInCameraAxis(regionId, cameraX, cameraY, cameraZ)) {
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
    private void bindScene() {
        LWJGL.glEnableClientState(NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_UNIFIED_NV);
        LWJGL.glEnableClientState(NVVertexBufferUnifiedMemory.GL_VERTEX_ATTRIB_ARRAY_UNIFIED_NV);
        LWJGL.glEnableClientState(NVVertexBufferUnifiedMemory.GL_ELEMENT_ARRAY_UNIFIED_NV);
        LWJGL.glEnableClientState(MeshShaderSupport.GL_DRAW_INDIRECT_UNIFIED_NV);
        LWJGL.glBufferAddressRangeNV(NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_ADDRESS_NV, 0, this.sceneUniform.getDeviceAddress(), SCENE_BYTES);
    }

    // Turns the unified-memory client states back off so vanilla's own draws are unaffected
    private void unbindScene() {
        LWJGL.glDisableClientState(NVUniformBufferUnifiedMemory.GL_UNIFORM_BUFFER_UNIFIED_NV);
        LWJGL.glDisableClientState(NVVertexBufferUnifiedMemory.GL_VERTEX_ATTRIB_ARRAY_UNIFIED_NV);
        LWJGL.glDisableClientState(NVVertexBufferUnifiedMemory.GL_ELEMENT_ARRAY_UNIFIED_NV);
        LWJGL.glDisableClientState(MeshShaderSupport.GL_DRAW_INDIRECT_UNIFIED_NV);
    }

    // Unpacks the GPU counters a download delivered
    private void readStatistics(long address) {
        this.regionCount = MemoryUtil.memGetInt(address);
        this.sectionCount = MemoryUtil.memGetInt(address + 4);
        this.quadCount = MemoryUtil.memGetInt(address + 8);
    }

    private void writeSceneUniform(ChunkRenderMatrices matrices, MeshFog fog, double cameraX, double cameraY, double cameraZ,
                                   int cameraSectionX, int cameraSectionY, int cameraSectionZ,
                                   int screenWidth, int screenHeight, int visibleCount) {
        // Geometry is section-relative, so the matrix carries the camera's in-section offset and the shader adds only a small section delta, which keeps big numbers out of the shader
        float deltaX = (float) -(cameraX - (cameraSectionX << 4));
        float deltaY = (float) -(cameraY - (cameraSectionY << 4));
        float deltaZ = (float) -(cameraZ - (cameraSectionZ << 4));

        long ptr = this.uploadStream.upload(this.sceneUniform, 0, SCENE_BYTES);

        this.mvp.set(matrices.projection())
                .mul(matrices.modelView())
                .translate(deltaX, deltaY, deltaZ)
                .getToAddress(ptr);
        ptr += 16 * 4;

        MemoryUtil.memPutInt(ptr, cameraSectionX);
        MemoryUtil.memPutInt(ptr + 4, cameraSectionY);
        MemoryUtil.memPutInt(ptr + 8, cameraSectionZ);
        MemoryUtil.memPutInt(ptr + 12, 0);
        ptr += 16;

        MemoryUtil.memPutFloat(ptr, deltaX);
        MemoryUtil.memPutFloat(ptr + 4, deltaY);
        MemoryUtil.memPutFloat(ptr + 8, deltaZ);
        MemoryUtil.memPutFloat(ptr + 12, 0.0f);
        ptr += 16;

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

        MemoryUtil.memPutShort(ptr, (short) visibleCount);
        MemoryUtil.memPutByte(ptr + 2, (byte) (this.frameId++));
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
