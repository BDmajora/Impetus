package com.bdmajora.impetus.engine.impl.render.mesh.region;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex;
import it.unimi.dsi.fastutil.longs.LongArrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import org.lwjgl.system.MemoryUtil;


// Merges every pass of one build into the single blob the mesh backend draws: translucent quads first, sorted back to front from the build camera, then every opaque pass's quads grouped by facing (Nvidium's repackage step)
public final class SectionGeometryPacker {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/MeshBackend");

    private static final int STRIDE = MeshChunkVertex.STRIDE;
    private static final int QUAD_BYTES = STRIDE * 4;
    // Section headers carry every count as a uint16
    private static final int MAX_GROUP_QUADS = 0xFFFF;

    private SectionGeometryPacker() {
    }

    // Packs a build's meshes; null when nothing was built, which tells the store to drop the section; relative camera is camera minus section origin, in blocks
    public static SectionGeometry pack(Map<TerrainRenderPass, BuiltSectionMeshParts> meshes, float cameraX, float cameraY, float cameraZ) {
        int translucentQuads = 0;
        int[] opaquePerFacing = new int[ModelQuadFacing.COUNT];

        for (var entry : meshes.entrySet()) {
            requireMeshLayout(entry.getKey());

            for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
                int quads = quadsIn(entry.getValue(), facing);

                if (entry.getKey().isSorted()) {
                    translucentQuads += quads;
                } else {
                    opaquePerFacing[facing.ordinal()] += quads;
                }
            }
        }

        // A group past 16 bits is pathological (every face of every block in the section and then some); the excess is dropped rather than letting the counts wrap
        translucentQuads = clampGroup(translucentQuads, "translucent");
        int total = translucentQuads;

        for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
            opaquePerFacing[facing.ordinal()] = clampGroup(opaquePerFacing[facing.ordinal()], facing.name());
            total += opaquePerFacing[facing.ordinal()];
        }

        if (total == 0) {
            return null;
        }

        NativeBuffer output = new NativeBuffer(total * QUAD_BYTES);
        long base = MemoryUtil.memAddress(output.getDirectBuffer());

        writeTranslucent(meshes, base, translucentQuads, cameraX, cameraY, cameraZ);

        long cursor = base + (long) translucentQuads * QUAD_BYTES;
        short[] quadsPerFacing = new short[ModelQuadFacing.COUNT];

        for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
            int budget = opaquePerFacing[facing.ordinal()];
            quadsPerFacing[facing.ordinal()] = (short) budget;

            for (var entry : meshes.entrySet()) {
                if (entry.getKey().isSorted() || budget == 0) {
                    continue;
                }

                int quads = Math.min(quadsIn(entry.getValue(), facing), budget);

                if (quads > 0) {
                    MemoryUtil.memCopy(rangeAddress(entry.getValue(), facing), cursor, (long) quads * QUAD_BYTES);
                    cursor += (long) quads * QUAD_BYTES;
                    budget -= quads;
                }
            }
        }

        return SectionGeometry.bounded(total, output, quadsPerFacing, (short) translucentQuads);
    }

    // Copies every translucent quad and orders them farthest first, the order blending needs; the GPU's per-frame swap pass only has to fix what camera movement disturbs
    private static void writeTranslucent(Map<TerrainRenderPass, BuiltSectionMeshParts> meshes, long destination, int quadCount,
                                         float cameraX, float cameraY, float cameraZ) {
        if (quadCount == 0) {
            return;
        }

        long[] sources = new long[quadCount];
        long[] keys = new long[quadCount];
        int index = 0;

        for (var entry : meshes.entrySet()) {
            if (!entry.getKey().isSorted()) {
                continue;
            }

            for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
                long quad = rangeAddress(entry.getValue(), facing);
                int quads = quadsIn(entry.getValue(), facing);

                for (int i = 0; i < quads && index < quadCount; i++, quad += QUAD_BYTES) {
                    sources[index] = quad;
                    // Distances are non-negative, so their float bits order like the floats; the low half carries the quad's slot
                    keys[index] = ((long) Float.floatToRawIntBits(distanceSquared(quad, cameraX, cameraY, cameraZ)) << 32) | index;
                    index++;
                }
            }
        }

        LongArrays.radixSort(keys);

        // Ascending distance, written from the end so the farthest quad lands first
        for (int i = 0; i < quadCount; i++) {
            long source = sources[(int) keys[i]];
            MemoryUtil.memCopy(source, destination + (long) (quadCount - 1 - i) * QUAD_BYTES, QUAD_BYTES);
        }
    }

    // Squared distance from the camera to a quad's centre, all in section-local blocks
    static float distanceSquared(long quad, float cameraX, float cameraY, float cameraZ) {
        float x = 0.0f, y = 0.0f, z = 0.0f;

        for (int vertex = 0; vertex < 4; vertex++) {
            long ptr = quad + (long) vertex * STRIDE;
            x += MeshChunkVertex.decodePosition(MeshChunkVertex.readPackedX(ptr));
            y += MeshChunkVertex.decodePosition(MeshChunkVertex.readPackedY(ptr));
            z += MeshChunkVertex.decodePosition(MeshChunkVertex.readPackedZ(ptr));
        }

        float dx = x * 0.25f - cameraX;
        float dy = y * 0.25f - cameraY;
        float dz = z * 0.25f - cameraZ;
        return dx * dx + dy * dy + dz * dz;
    }

    // Quads one pass built for one facing
    private static int quadsIn(BuiltSectionMeshParts mesh, ModelQuadFacing facing) {
        VertexRange range = mesh.ranges().get(facing);
        return range == null ? 0 : range.vertexCount() >> 2;
    }

    // Native address of a facing's first quad in a pass's vertex buffer
    private static long rangeAddress(BuiltSectionMeshParts mesh, ModelQuadFacing facing) {
        VertexRange range = mesh.ranges().get(facing);
        long start = range == null ? 0L : (long) range.vertexStart() * STRIDE;
        return MemoryUtil.memAddress(mesh.vertexBuffer().getDirectBuffer()) + start;
    }

    // The mesh shaders read four 16-byte vertices per quad and nothing else
    private static void requireMeshLayout(TerrainRenderPass pass) {
        if (pass.vertexType().getVertexFormat().getStride() != STRIDE || pass.primitiveType().getVerticesPerPrimitive() != 4) {
            throw new IllegalStateException("Pass " + pass.name() + " was not built in the mesh vertex layout");
        }
    }

    // Caps one header count at 16 bits
    private static int clampGroup(int quads, String group) {
        if (quads > MAX_GROUP_QUADS) {
            LOGGER.warn("Section has {} {} quads, more than a mesh header can count; drawing the first {}", quads, group, MAX_GROUP_QUADS);
            return MAX_GROUP_QUADS;
        }
        return quads;
    }
}
