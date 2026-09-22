package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.BindlessBuffer;
import com.bdmajora.impetus.engine.impl.render.mesh.region.MeshRegionStore;
import com.bdmajora.impetus.engine.impl.render.mesh.region.MeshSectionStore;
import com.bdmajora.impetus.engine.impl.render.mesh.renderers.RegionRasterizer;
import com.bdmajora.impetus.engine.impl.render.mesh.renderers.SectionRasterizer;
import com.bdmajora.impetus.engine.impl.render.mesh.renderers.TerrainRasterizer;
import com.bdmajora.impetus.engine.impl.render.mesh.util.QuadArena;
import com.bdmajora.impetus.engine.impl.render.mesh.util.UploadStream;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.lwjgl.GL11;
import com.bdmajora.impetus.lwjgl.GL42;
import com.bdmajora.impetus.lwjgl.GL43;
import com.bdmajora.impetus.lwjgl.GLNv;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import org.joml.Matrix4f;

import java.util.BitSet;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Mesh-shader terrain path: regions and sections live in bindless GPU buffers, a task shader culls regions and a mesh shader culls sections and emits quads, one CPU draw per frame; gated by MeshShaderSupport
public class MeshRenderPipeline {
    // Cap on live regions, sizing every per-region buffer; render distance 64 needs ~17*17*4 = 1200 (8x4x8 regions, four vertical layers), and 4096 leaves headroom without the section header buffer reaching hundreds of MB
    private static final int MAX_REGIONS = 4096;

    // Scene uniform block, std140. Layout must match scene.glsl exactly, field for field
    private static final int SCENE_BYTES = align(
            16 * 4    // mat4 MVP
                    + 16      // ivec4 cameraChunk
                    + 16      // vec4 subChunkOffset
                    + 16      // vec4 fogColour
                    + 8 * 8   // eight 64-bit device pointers
                    + 8       // vec2 screenSize
                    + 4 + 4   // fog start, fog end
                    + 4       // int fogShape
                    + 2       // uint16 regionCount
                    + 1,      // uint8 frameId
            16);

    private final UploadStream uploadStream;
    private final MeshSectionStore sections;

    private final BindlessBuffer sceneUniform;
    private final BindlessBuffer regionVisibility;
    private final BindlessBuffer sectionVisibility;
    private final BindlessBuffer terrainCommands;

    private final RegionRasterizer regionRasterizer;
    private final SectionRasterizer sectionRasterizer;
    private final TerrainRasterizer terrainRasterizer;

    // Regions inside the frustum last frame, so one leaving it has its stale section visibility bytes cleared instead of staying "visible" forever
    private final BitSet regionsInFrustum = new BitSet(MAX_REGIONS);

    // Reused across frames; the sort key is (distance << 16) | regionId, sorted once per frame so iteration order is front-to-back without a tree node per region
    private final IntArrayList visibleRegions = new IntArrayList();

    private final Matrix4f mvp = new Matrix4f();

    private int previousRegionCount;
    private int frameId;

    public MeshRenderPipeline(long uploadBufferSize, long geometryBudget) {
        this.uploadStream = new UploadStream(uploadBufferSize);

        MeshRegionStore regions = new MeshRegionStore(MAX_REGIONS, this.uploadStream);
        QuadArena arena = new QuadArena(geometryBudget, MeshChunkVertex.STRIDE);
        this.sections = new MeshSectionStore(regions, arena, this.uploadStream);

        // The visible region id list sits right after the scene struct in the same allocation, reached by pointer arithmetic off the UBO's address
        this.sceneUniform = new BindlessBuffer(SCENE_BYTES + MAX_REGIONS * 2L);
        this.regionVisibility = new BindlessBuffer(MAX_REGIONS);
        this.sectionVisibility = new BindlessBuffer(MAX_REGIONS * (long) MeshRegionStore.SECTIONS_PER_REGION);
        this.terrainCommands = new BindlessBuffer(MAX_REGIONS * 8L);

        this.regionVisibility.clear();
        this.sectionVisibility.clear();
        this.terrainCommands.clear();

        this.regionRasterizer = new RegionRasterizer();
        this.sectionRasterizer = new SectionRasterizer();
        this.terrainRasterizer = new TerrainRasterizer();
    }

    // Section geometry and metadata
    public MeshSectionStore getSections() {
        return this.sections;
    }

    // The staging ring uploads go through
    public UploadStream getUploadStream() {
        return this.uploadStream;
    }

    // Draws one frame of terrain and computes next frame's visibility; camera is exact world position, atlas and lightmap are GL texture names since this package knows nothing about Minecraft
    public void renderFrame(Viewport viewport, ChunkRenderMatrices matrices,
                            double cameraX, double cameraY, double cameraZ,
                            int screenWidth, int screenHeight) {
        MeshRegionStore regions = this.sections.getRegions();

        if (regions.getRegionCount() == 0) {
            return;
        }

        int cameraSectionX = (int) Math.floor(cameraX) >> 4;
        int cameraSectionY = (int) Math.floor(cameraY) >> 4;
        int cameraSectionZ = (int) Math.floor(cameraZ) >> 4;

        int visibleCount = collectVisibleRegions(regions, viewport, cameraSectionX, cameraSectionY, cameraSectionZ);

        if (visibleCount == 0) {
            return;
        }

        writeSceneUniform(matrices, cameraX, cameraY, cameraZ,
                cameraSectionX, cameraSectionY, cameraSectionZ, screenWidth, screenHeight, visibleCount);

        // Everything the shaders are about to read has to be resident before the first draw
        this.sections.commit();
        this.uploadStream.commit();

        // Address-based binding stays live across program changes, unlike a bound UBO, so it is set once per frame
        LWJGL.glEnableClientState(GLNv.GL_UNIFORM_BUFFER_UNIFIED_NV);
        LWJGL.glEnableClientState(GLNv.GL_VERTEX_ATTRIB_ARRAY_UNIFIED_NV);
        LWJGL.glEnableClientState(GLNv.GL_ELEMENT_ARRAY_UNIFIED_NV);
        LWJGL.glEnableClientState(GLNv.GL_DRAW_INDIRECT_UNIFIED_NV);
        LWJGL.glBufferAddressRangeNV(GLNv.GL_UNIFORM_BUFFER_ADDRESS_NV, 0,
                this.sceneUniform.getDeviceAddress(), SCENE_BYTES);

        // ---- the terrain draw, from last frame's commands ----
        if (this.previousRegionCount != 0) {
            LWJGL.glEnable(GL11.GL_DEPTH_TEST);
            this.terrainRasterizer.raster(this.previousRegionCount, this.terrainCommands.getDeviceAddress());
            LWJGL.glMemoryBarrier(GL42.GL_FRAMEBUFFER_BARRIER_BIT);
        }

        // Visibility for the next frame; depth writes stay off because the representative fragment test needs it and occlusion boxes must not pollute the terrain's depth buffer
        LWJGL.glEnable(GL11.GL_DEPTH_TEST);
        LWJGL.glDepthFunc(GL11.GL_LEQUAL);
        LWJGL.glDepthMask(false);
        LWJGL.glColorMask(false, false, false, false);
        LWJGL.glEnable(GLNv.GL_REPRESENTATIVE_FRAGMENT_TEST_NV);

        this.regionRasterizer.raster(visibleCount);
        LWJGL.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

        this.sectionRasterizer.raster(visibleCount);
        LWJGL.glMemoryBarrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

        LWJGL.glDisable(GLNv.GL_REPRESENTATIVE_FRAGMENT_TEST_NV);
        LWJGL.glDepthMask(true);
        LWJGL.glColorMask(true, true, true, true);

        // The commands the section rasteriser just wrote are what the next frame draws from
        LWJGL.glMemoryBarrier(GL42.GL_COMMAND_BARRIER_BIT);
        this.previousRegionCount = visibleCount;

        LWJGL.glDisableClientState(GLNv.GL_UNIFORM_BUFFER_UNIFIED_NV);
        LWJGL.glDisableClientState(GLNv.GL_VERTEX_ATTRIB_ARRAY_UNIFIED_NV);
        LWJGL.glDisableClientState(GLNv.GL_ELEMENT_ARRAY_UNIFIED_NV);
        LWJGL.glDisableClientState(GLNv.GL_DRAW_INDIRECT_UNIFIED_NV);
        LWJGL.glDisable(GL11.GL_DEPTH_TEST);

        this.uploadStream.endFrame();
    }

    // Frees every buffer and program
    public void delete() {
        this.regionRasterizer.delete();
        this.sectionRasterizer.delete();
        this.terrainRasterizer.delete();

        this.sceneUniform.delete();
        this.regionVisibility.delete();
        this.sectionVisibility.delete();
        this.terrainCommands.delete();

        this.sections.getArena().delete();
        this.sections.getRegions().delete();
        this.uploadStream.delete();
    }

    // Frustum-culls regions, sorts front-to-back (near boxes only occlude far ones if drawn first) and writes the id list the shaders index by workgroup
    private int collectVisibleRegions(MeshRegionStore regions, Viewport viewport,
                                      int cameraSectionX, int cameraSectionY, int cameraSectionZ) {
        this.visibleRegions.clear();

        for (int regionId = 0; regionId < regions.getMaxRegionIndex(); regionId++) {
            if (!regions.regionExists(regionId)) {
                continue;
            }

            if (regions.isRegionVisible(viewport, regionId)) {
                int distance = regions.distanceTo(regionId, cameraSectionX, cameraSectionY, cameraSectionZ);
                this.visibleRegions.add((distance << 16) | regionId);
                this.regionsInFrustum.set(regionId);
            } else if (this.regionsInFrustum.get(regionId)) {
                // Just left the frustum: its sections were never tested this frame and their stale "visible" bytes would resurrect it the moment it returns
                this.sectionVisibility.clearRange((long) regionId << 8, MeshRegionStore.SECTIONS_PER_REGION);
                this.regionsInFrustum.clear(regionId);
            }
        }

        int count = this.visibleRegions.size();

        if (count == 0) {
            return 0;
        }

        int[] keys = this.visibleRegions.elements();
        IntArrays.quickSort(keys, 0, count);

        long ptr = this.uploadStream.upload(this.sceneUniform, SCENE_BYTES, count * 2L);

        for (int index = 0; index < count; index++) {
            LWJGL.memPutShort(ptr + ((long) index << 1), (short) (keys[index] & 0xFFFF));
        }

        return count;
    }

    private void writeSceneUniform(ChunkRenderMatrices matrices, double cameraX, double cameraY, double cameraZ,
                                   int cameraSectionX, int cameraSectionY, int cameraSectionZ,
                                   int screenWidth, int screenHeight, int visibleCount) {
        // Geometry is section-relative, so the matrix carries the camera's in-section offset and the shader adds only a small integer section delta; keeping big numbers out of the shader stops distant shimmer
        float deltaX = (float) -(cameraX - (cameraSectionX << 4));
        float deltaY = (float) -(cameraY - (cameraSectionY << 4));
        float deltaZ = (float) -(cameraZ - (cameraSectionZ << 4));

        long ptr = this.uploadStream.upload(this.sceneUniform, 0, SCENE_BYTES);

        this.mvp.set(matrices.projection())
                .mul(matrices.modelView())
                .translate(deltaX, deltaY, deltaZ)
                .getToAddress(ptr);
        ptr += 16 * 4;

        LWJGL.memPutInt(ptr, cameraSectionX);
        LWJGL.memPutInt(ptr + 4, cameraSectionY);
        LWJGL.memPutInt(ptr + 8, cameraSectionZ);
        LWJGL.memPutInt(ptr + 12, 0);
        ptr += 16;

        LWJGL.memPutFloat(ptr, deltaX);
        LWJGL.memPutFloat(ptr + 4, deltaY);
        LWJGL.memPutFloat(ptr + 8, deltaZ);
        LWJGL.memPutFloat(ptr + 12, 0.0f);
        ptr += 16;

        // Fog is not wired up yet; the shaders compile without RENDER_FOG, so this stays zero
        LWJGL.memSet(ptr, 0, 16);
        ptr += 16;

        MeshRegionStore regions = this.sections.getRegions();

        // The pointer table, in the order scene.glsl declares it
        ptr = putPointer(ptr, this.sceneUniform.getDeviceAddress() + SCENE_BYTES);
        ptr = putPointer(ptr, regions.getRegionBufferAddress());
        ptr = putPointer(ptr, regions.getSectionBufferAddress());
        ptr = putPointer(ptr, this.regionVisibility.getDeviceAddress());
        ptr = putPointer(ptr, this.sectionVisibility.getDeviceAddress());
        ptr = putPointer(ptr, this.terrainCommands.getDeviceAddress());
        ptr = putPointer(ptr, this.sections.getArena().getBuffer().getDeviceAddress());
        ptr = putPointer(ptr, 0L);

        // Half-extents, because the mesh shader maps clip space to pixels with a single multiply per vertex
        LWJGL.memPutFloat(ptr, screenWidth / 2.0f);
        LWJGL.memPutFloat(ptr + 4, screenHeight / 2.0f);
        ptr += 8;

        LWJGL.memPutFloat(ptr, 0.0f);
        LWJGL.memPutFloat(ptr + 4, 0.0f);
        LWJGL.memPutInt(ptr + 8, 0);
        ptr += 12;

        LWJGL.memPutShort(ptr, (short) visibleCount);
        LWJGL.memPutByte(ptr + 2, (byte) (this.frameId++));
    }

    // Writes a 64-bit address and advances
    private static long putPointer(long ptr, long address) {
        LWJGL.memPutLong(ptr, address);
        return ptr + 8;
    }

    // Rounds up to a multiple
    private static int align(int value, int alignment) {
        int remainder = value % alignment;
        return remainder == 0 ? value : value + (alignment - remainder);
    }
}
