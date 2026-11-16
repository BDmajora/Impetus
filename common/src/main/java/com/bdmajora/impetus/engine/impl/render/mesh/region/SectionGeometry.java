package com.bdmajora.impetus.engine.impl.render.mesh.region;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.util.VertexRange;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.data.BuiltSectionMeshParts;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.impl.MeshChunkVertex;
import org.lwjgl.system.MemoryUtil;

// Geometry of a section in the layout of the mesh shader.
public record SectionGeometry(
        // Total quads of every facing.
        int quadCount,
        // Vertex bytes grouped by facing.
        NativeBuffer geometry,
        // Quads of each facing.
        short[] quadsPerFacing,
        // Quad index where the first facing starts.
        short baseQuad,
        // Lowest cell of the bounding box.
        int minX, int minY, int minZ,
        // Size of the bounding box.
        int sizeX, int sizeY, int sizeZ
) {
    // Frees the geometry.
    public void delete() {
        this.geometry.free();
    }

    // Reshapes the mesh of a pass and returns null when it has no quads.
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

            int quads = range.vertexCount() >> 2;
            if (quads > 0xFFFF) {
                throw new IllegalStateException("Too many quads in one facing: " + quads);
            }
            quadsPerFacing[facing.ordinal()] = (short) quads;
            total += quads;
            firstVertex = Math.min(firstVertex, range.vertexStart());
        }

        if (total == 0) {
            return null;
        }

        if ((firstVertex >> 2) > 0xFFFF) {
            throw new IllegalStateException("Base quad out of range: " + (firstVertex >> 2));
        }

        BoundingBox box = computeBounds(vertices, firstVertex, total, MeshChunkVertex.STRIDE);

        return new SectionGeometry(total, vertices, quadsPerFacing, (short) (firstVertex >> 2),
                box.minX, box.minY, box.minZ, box.sizeX, box.sizeY, box.sizeZ);
    }

    // Wraps packed geometry and measures its bounds and rejects a count that is not positive.
    public static SectionGeometry bounded(int quadCount, NativeBuffer geometry, short[] quadsPerFacing, short baseQuad, int stride) {
        if (quadCount <= 0) {
            throw new IllegalArgumentException("Quad count must be positive");
        }
        BoundingBox box = computeBounds(geometry, 0, quadCount, stride);

        return new SectionGeometry(quadCount, geometry, quadsPerFacing, baseQuad,
                box.minX, box.minY, box.minZ, box.sizeX, box.sizeY, box.sizeZ);
    }

    // Returns the bounds in whole blocks and rejects geometry that runs past its buffer.
    private static BoundingBox computeBounds(NativeBuffer vertices, int firstVertex, int quadCount, int stride) {
        if (stride <= 0 || firstVertex < 0 || ((long) firstVertex + 4L * quadCount) * stride > vertices.getLength()) {
            throw new IllegalArgumentException("Geometry runs past the end of its buffer");
        }
        long ptr = MemoryUtil.memAddress(vertices.getDirectBuffer()) + (long) firstVertex * stride;
        long end = ptr + (long) quadCount * 4L * stride;

        int minX = 16, minY = 16, minZ = 16;
        int maxX = -1, maxY = -1, maxZ = -1;

        for (; ptr < end; ptr += stride) {
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

        return new BoundingBox(minX, minY, minZ, maxX - minX, maxY - minY, maxZ - minZ);
    }

    // Bounds of the geometry inside a section.
    private record BoundingBox(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {}
}
