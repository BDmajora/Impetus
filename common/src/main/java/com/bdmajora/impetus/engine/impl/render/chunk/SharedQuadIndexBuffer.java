package com.bdmajora.impetus.engine.impl.render.chunk;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapFlags;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferUsage;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;
import com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting.ChunkPrimitiveType;

import java.util.Objects;

// Index buffer shared by every draw of quads.
public class SharedQuadIndexBuffer {
    // Buffer of the indices.
    private final GlMutableBuffer buffer;
    // Primitive type of the indices.
    private final ChunkPrimitiveType primitiveType;

    // Number of primitives that the buffer holds.
    private int maxPrimitives;

    // Creates the buffer and rejects a null primitive type.
    public SharedQuadIndexBuffer(CommandList commandList, ChunkPrimitiveType primitiveType) {
        this.primitiveType = Objects.requireNonNull(primitiveType, "primitiveType");
        this.buffer = commandList.createMutableBuffer();
    }

    // Grows the buffer when a draw needs more indices.
    public void ensureCapacity(CommandList commandList, int elementCount) {
        int primitiveCount = elementCount / primitiveType.getIndexBufferElementsPerPrimitive();

        if (primitiveCount > this.maxPrimitives) {
            this.grow(commandList, this.getNextSize(primitiveCount));
        }
    }

    // Returns the size to grow to and rejects an overflow.
    private int getNextSize(int primitiveCount) {
        return Math.max(Math.multiplyExact(this.maxPrimitives, 2), Math.addExact(primitiveCount, 16384));
    }

    // Replaces the storage and writes the sequential indices.
    private void grow(CommandList commandList, int primitiveCount) {
        var bufferSize = primitiveType.getIndexBufferSize(primitiveCount);

        commandList.allocateStorage(this.buffer, bufferSize, GlBufferUsage.STATIC_DRAW);

        var mapped = commandList.mapBuffer(this.buffer, 0, bufferSize, EnumBitField.of(GlBufferMapFlags.INVALIDATE_BUFFER, GlBufferMapFlags.WRITE, GlBufferMapFlags.UNSYNCHRONIZED));
        // Unmaps the buffer on every path
        try {
            this.primitiveType.generateSimpleIndexBuffer(mapped.getMemoryBuffer(), primitiveCount);
        } finally {
            commandList.unmap(mapped);
        }

        this.maxPrimitives = primitiveCount;
    }

    // Returns the buffer.
    public GlBuffer getBufferObject() {
        return this.buffer;
    }

    // Deletes the buffer.
    public void delete(CommandList commandList) {
        commandList.deleteBuffer(this.buffer);
    }
}
