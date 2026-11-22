package com.bdmajora.impetus.engine.impl.render.mesh.region;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.chunk.terrain.TerrainRenderPass;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshVertexLayout;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import org.lwjgl.system.MemoryUtil;

// Merges the passes of a build into one block of quads.
public final class SectionGeometryPacker {
    // Logger of the backend.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/MeshBackend");

    // Largest number of quads in one group.
    private static final int MAX_GROUP_QUADS = 0xFFFF;

    // Prevents instantiation
    private SectionGeometryPacker() {
    }

    // Packs a build whose passes use a mesh layout and returns null otherwise.
    public static SectionGeometry packForMeshBackend(Map<TerrainRenderPass, BuiltSectionMeshParts> meshes) {
        java.util.Objects.requireNonNull(meshes, "meshes");
        for (TerrainRenderPass pass : meshes.keySet()) {
            if (!(pass.vertexType() instanceof MeshVertexLayout)) {
                return null;
            }
        }
        return pack(meshes);
    }

    // Packs the passes of a build and returns null when nothing was built.
    public static SectionGeometry pack(Map<TerrainRenderPass, BuiltSectionMeshParts> meshes) {
        java.util.Objects.requireNonNull(meshes, "meshes");
        int stride = 0;
        int translucentQuads = 0;
        int[] opaquePerFacing = new int[ModelQuadFacing.COUNT];

        for (var entry : meshes.entrySet()) {
            stride = requireMeshLayout(entry.getKey(), stride);

            for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
                int quads = quadsIn(entry.getValue(), facing);

                if (entry.getKey().isReverseOrder()) {
                    translucentQuads += quads;
                } else {
                    opaquePerFacing[facing.ordinal()] += quads;
                }
            }
        }

        translucentQuads = clampGroup(translucentQuads, "translucent");
        int total = translucentQuads;

        for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
            opaquePerFacing[facing.ordinal()] = clampGroup(opaquePerFacing[facing.ordinal()], facing.name());
            total += opaquePerFacing[facing.ordinal()];
        }

        if (total == 0) {
            return null;
        }

        int quadBytes = Math.multiplyExact(stride, 4);
        NativeBuffer output = new NativeBuffer(Math.multiplyExact(total, quadBytes));

        try {
            long base = MemoryUtil.memAddress(output.getDirectBuffer());

            writeTranslucent(meshes, base, translucentQuads, stride);

            long cursor = base + (long) translucentQuads * quadBytes;
            short[] quadsPerFacing = new short[ModelQuadFacing.COUNT];

            for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
                int budget = opaquePerFacing[facing.ordinal()];
                quadsPerFacing[facing.ordinal()] = (short) budget;

                for (var entry : meshes.entrySet()) {
                    if (entry.getKey().isReverseOrder() || budget == 0) {
                        continue;
                    }

                    int quads = Math.min(quadsIn(entry.getValue(), facing), budget);

                    if (quads > 0) {
                        MemoryUtil.memCopy(rangeAddress(entry.getValue(), facing, stride), cursor, (long) quads * quadBytes);
                        cursor += (long) quads * quadBytes;
                        budget -= quads;
                    }
                }
            }

            return SectionGeometry.bounded(total, output, quadsPerFacing, (short) translucentQuads, stride);
        } catch (Throwable failure) {
            output.free();
            throw failure;
        }
    }

    // Copies translucent vertices in source order so the uploaded indices can address them.
    private static void writeTranslucent(Map<TerrainRenderPass, BuiltSectionMeshParts> meshes, long destination, int quadCount, int stride) {
        int quadBytes = stride * 4;
        for (var entry : meshes.entrySet()) {
            if (!entry.getKey().isReverseOrder()) {
                continue;
            }
            for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
                int quads = Math.min(quadsIn(entry.getValue(), facing), quadCount);
                if (quads > 0) {
                    long bytes = (long) quads * quadBytes;
                    MemoryUtil.memCopy(rangeAddress(entry.getValue(), facing, stride), destination, bytes);
                    destination += bytes;
                    quadCount -= quads;
                }
            }
        }
    }

    // Returns the quads of a pass in a facing.
    private static int quadsIn(BuiltSectionMeshParts mesh, ModelQuadFacing facing) {
        VertexRange range = mesh.ranges().get(facing);
        return range == null ? 0 : range.vertexCount() >> 2;
    }

    // Returns the address of the first quad of a facing.
    private static long rangeAddress(BuiltSectionMeshParts mesh, ModelQuadFacing facing, int stride) {
        VertexRange range = mesh.ranges().get(facing);
        long start = range == null ? 0L : (long) range.vertexStart() * stride;
        return MemoryUtil.memAddress(mesh.vertexBuffer().getDirectBuffer()) + start;
    }

    // Returns the stride of a pass and rejects a pass that is not in the mesh layout.
    private static int requireMeshLayout(TerrainRenderPass pass, int expectedStride) {
        int stride = pass.vertexType().getVertexFormat().getStride();

        if (!(pass.vertexType() instanceof MeshVertexLayout) || pass.primitiveType().getVerticesPerPrimitive() != 4
                || (expectedStride != 0 && stride != expectedStride)) {
            throw new IllegalStateException("Pass " + pass.name() + " was not built in the mesh vertex layout");
        }

        return stride;
    }

    // Returns the quads limited to the largest group.
    private static int clampGroup(int quads, String group) {
        if (quads > MAX_GROUP_QUADS) {
            LOGGER.warn("Section has {} {} quads, more than a mesh header can count; drawing the first {}", quads, group, MAX_GROUP_QUADS);
            return MAX_GROUP_QUADS;
        }
        return quads;
    }
}
