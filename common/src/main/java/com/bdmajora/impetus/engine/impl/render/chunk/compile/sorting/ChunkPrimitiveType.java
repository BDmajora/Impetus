package com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting;

import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.SortState;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.List;

// Kind of primitive that terrain geometry is drawn as.
public interface ChunkPrimitiveType {
    // Returns the bytes of an index buffer for a number of primitives and rejects a negative count or an overflow.
    default int getIndexBufferSize(int numPrimitives) {
        if (numPrimitives < 0) {
            throw new IllegalArgumentException("Negative primitive count " + numPrimitives);
        }
        return Math.multiplyExact(Math.multiplyExact(numPrimitives, getIndexBufferElementsPerPrimitive()), 4);
    }

    // Returns the number of vertices of a primitive.
    int getVerticesPerPrimitive();

    // Returns the number of indices of a primitive.
    int getIndexBufferElementsPerPrimitive();

    // Returns the GL primitive of the draw call.
    GlPrimitiveType getGlPrimitiveType();

    // Writes indices that draw the primitives in vertex order.
    void generateSimpleIndexBuffer(ByteBuffer indexBuffer, int numPrimitives);

    // Writes indices that draw the primitives from far to near for a camera position.
    void generateSortedIndexBuffer(ByteBuffer indexBuffer, int numPrimitives, @Nullable SortState chunkData, float x, float y, float z);

    // Returns the shader definitions the primitive needs.
    default List<String> getDefines() {
        return List.of();
    }
}
