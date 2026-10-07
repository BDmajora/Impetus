package com.bdmajora.impetus.engine.impl.render.mesh.region;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex;
import org.lwjgl.system.MemoryUtil;


// A finished build reshaped for the mesh pipeline (raw vertex bytes, per-facing quad counts, bounding box), produced on a build worker since doing it in uploadChunks wrecks 1% lows
public record SectionGeometry(
        // Total quads across every facing
        int quadCount,
        // Vertex bytes in MeshChunkVertex layout, four per quad, grouped by facing in ModelQuadFacing ordinal order
        NativeBuffer geometry,
        // Quads per facing, indexed by ModelQuadFacing.ordinal(); slot 6 is UNASSIGNED, which is always drawn
        short[] quadsPerFacing,
        // Quad index within the section's own geometry where the first facing's quads start; the packer puts the translucent quads before it, so it doubles as their count
        short baseQuad,
        // Section-relative bounding box in 1/16-block units, each component 0..15
        int minX, int minY, int minZ,
        int sizeX, int sizeY, int sizeZ
) {
    // Returns the quad allocation to the arena
    public void delete() {
        this.geometry.free();
    }

    // Reshapes one pass's build output; null when the pass produced nothing, which signals dropping the section rather than uploading an empty one
    public static SectionGeometry from(BuiltSectionMeshParts mesh) {
        NativeBuffer vertices = mesh.vertexBuffer();

        if (vertices == null || vertices.getLength() == 0) {
            return null;
        }

        short[] quadsPerFacing = new short[ModelQuadFacing.COUNT];
        int total = 0;
        int firstVertex = Integer.MAX_VALUE;

        for (ModelQuadFacing facing : ModelQuadFacing.VALUES) {
            VertexRange range = mesh.ranges().get(facing);

            if (range == null || range.vertexCount() == 0) {
                continue;
            }

            // Four vertices to a quad; the mesher never emits a partial one
            int quads = range.vertexCount() >> 2;
            quadsPerFacing[facing.ordinal()] = (short) quads;
            total += quads;
            firstVertex = Math.min(firstVertex, range.vertexStart());
        }

        if (total == 0) {
            return null;
        }

        BoundingBox box = computeBounds(vertices, firstVertex, total);

        return new SectionGeometry(total, vertices, quadsPerFacing, (short) (firstVertex >> 2),
                box.minX, box.minY, box.minZ, box.sizeX, box.sizeY, box.sizeZ);
    }

    // Wraps already-packed geometry (every quad from the buffer's start) and measures its bounds
    public static SectionGeometry bounded(int quadCount, NativeBuffer geometry, short[] quadsPerFacing, short baseQuad) {
        BoundingBox box = computeBounds(geometry, 0, quadCount);

        return new SectionGeometry(quadCount, geometry, quadsPerFacing, baseQuad,
                box.minX, box.minY, box.minZ, box.sizeX, box.sizeY, box.sizeZ);
    }

    // Snaps the geometry to a 16x16x16 grid of 1-block cells (the occlusion box granularity); a tight box is what lets a section behind a wall fail the depth test
    private static BoundingBox computeBounds(NativeBuffer vertices, int firstVertex, int quadCount) {
        long ptr = MemoryUtil.memAddress(vertices.getDirectBuffer()) + (long) firstVertex * MeshChunkVertex.STRIDE;
        long end = ptr + (long) quadCount * 4L * MeshChunkVertex.STRIDE;

        int minX = 16, minY = 16, minZ = 16;
        int maxX = -1, maxY = -1, maxZ = -1;

        for (; ptr < end; ptr += MeshChunkVertex.STRIDE) {
            int x = MeshChunkVertex.decodeBlockCoord(MeshChunkVertex.readPackedX(ptr));
            int y = MeshChunkVertex.decodeBlockCoord(MeshChunkVertex.readPackedY(ptr));
            int z = MeshChunkVertex.decodeBlockCoord(MeshChunkVertex.readPackedZ(ptr));

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }

        // An empty span cannot happen (quadCount > 0) but a degenerate one can if every vertex shares a cell; size 0 is correct and the shader adds the extra block
        return new BoundingBox(minX, minY, minZ, maxX - minX, maxY - minY, maxZ - minZ);
    }

    // Section-local bounds of the geometry, for the mesh shader's cull
    private record BoundingBox(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {}
}
