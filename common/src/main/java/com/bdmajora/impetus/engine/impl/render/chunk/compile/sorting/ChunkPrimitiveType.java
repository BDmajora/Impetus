package com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting;

import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.TranslucentQuadAnalyzer;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.List;

public interface ChunkPrimitiveType {
    // Bytes for an index buffer of this many primitives
    default int getIndexBufferSize(int numPrimitives) {
        return numPrimitives * getIndexBufferElementsPerPrimitive() * 4;
    }

    // the number of vertices in a primitive, e.g. 4 for quads, 3 for triangles
    int getVerticesPerPrimitive();

    // the number of index buffer elements per primitive, e.g. 6 for quads, 3 for triangles
    int getIndexBufferElementsPerPrimitive();

    // What the GL draw call is issued as
    GlPrimitiveType getGlPrimitiveType();

    // Generates a "simple" index buffer drawing numPrimitives in vertex-buffer order; the caller supplies a buffer sized by getIndexBufferSize(int)
    void generateSimpleIndexBuffer(ByteBuffer indexBuffer, int numPrimitives);

    // Generates a sorted index buffer for numPrimitives from chunkData; x/y/z is the subchunk-relative camera so it compares directly against SortState positions
    void generateSortedIndexBuffer(ByteBuffer indexBuffer, int numPrimitives, @Nullable TranslucentQuadAnalyzer.SortState chunkData, float x, float y, float z);

    // Shader defines this primitive type needs
    default List<String> getDefines() {
        return List.of();
    }
}
