package com.bdmajora.impetus.engine.impl.render.chunk.compile.sorting;

import com.bdmajora.impetus.engine.impl.gl.tessellation.GlPrimitiveType;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.PartitionTree;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.SortState;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.TranslucentSorter;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

// Quad primitive type that draws as triangles or as quads.
public final class QuadPrimitiveType implements ChunkPrimitiveType {
    // Type that splits each quad into two triangles.
    public static final QuadPrimitiveType TRIANGULATED = new QuadPrimitiveType(true);
    // Type that draws each quad as one quad primitive.
    public static final QuadPrimitiveType DIRECT = new QuadPrimitiveType(false);

    // Triangle split state of the type.
    private final boolean triangulating;

    // Number of vertices of a quad.
    private static final int VERTICES_PER_PRIMITIVE = 4;

    // Creates a type that splits quads into triangles or keeps them whole.
    public QuadPrimitiveType(boolean triangulating) {
        this.triangulating = triangulating;
    }

    // Returns six indices for triangles and four for quads.
    @Override
    public int getIndexBufferElementsPerPrimitive() {
        return triangulating ? 6 : 4;
    }

    // Returns the GL primitive of the draw call.
    @Override
    public GlPrimitiveType getGlPrimitiveType() {
        return triangulating ? GlPrimitiveType.TRIANGLES : GlPrimitiveType.QUADS;
    }

    // Returns four.
    @Override
    public int getVerticesPerPrimitive() {
        return VERTICES_PER_PRIMITIVE;
    }

    // Writes indices in vertex order from the buffer position and rejects a buffer with too few remaining bytes.
    @Override
    public void generateSimpleIndexBuffer(ByteBuffer indexBuffer, int numPrimitives) {
        int minimumRequiredBufferSize = getIndexBufferSize(numPrimitives);
        if (indexBuffer.remaining() < minimumRequiredBufferSize) {
            throw new IllegalStateException("Given index buffer has " + indexBuffer.remaining() + " remaining bytes but we need " + minimumRequiredBufferSize);
        }
        long ptr = MemoryUtil.memAddress(indexBuffer);

        for (int primitiveIndex = 0; primitiveIndex < numPrimitives; primitiveIndex++) {
            this.writePrimitive(ptr, primitiveIndex, primitiveIndex);
        }
    }

    // Writes the indices of a quad as the primitive at a position.
    private void writePrimitive(long ptr, int primitiveIndex, int quad) {
        int indexOffset = primitiveIndex * this.getIndexBufferElementsPerPrimitive();
        int vertexOffset = quad * VERTICES_PER_PRIMITIVE;

        MemoryUtil.memPutInt(ptr + (indexOffset + 0) * 4, vertexOffset + 0);
        MemoryUtil.memPutInt(ptr + (indexOffset + 1) * 4, vertexOffset + 1);
        MemoryUtil.memPutInt(ptr + (indexOffset + 2) * 4, vertexOffset + 2);

        if (this.triangulating) {
            MemoryUtil.memPutInt(ptr + (indexOffset + 3) * 4, vertexOffset + 2);
            MemoryUtil.memPutInt(ptr + (indexOffset + 4) * 4, vertexOffset + 3);
            MemoryUtil.memPutInt(ptr + (indexOffset + 5) * 4, vertexOffset + 0);
        } else {
            MemoryUtil.memPutInt(ptr + (indexOffset + 3) * 4, vertexOffset + 3);
        }
    }

    // Writes the indices of quads in a given order and rejects a buffer whose remaining bytes differ from the size.
    private void generateIndexBuffer(ByteBuffer indexBuffer, int[] primitiveMapping) {
        int bufferSize = getIndexBufferSize(primitiveMapping.length);
        if (indexBuffer.remaining() != bufferSize) {
            throw new IllegalStateException("Given index buffer has " + indexBuffer.remaining() + " remaining bytes but we expected " + bufferSize);
        }
        long ptr = MemoryUtil.memAddress(indexBuffer);

        for (int primitiveIndex = 0; primitiveIndex < primitiveMapping.length; primitiveIndex++) {
            this.writePrimitive(ptr, primitiveIndex, primitiveMapping[primitiveIndex]);
        }
    }

    // Sink that writes the quads of a partition tree into the index buffer.
    private final class BufferSink implements PartitionTree.Sink {
        // Address of the index buffer.
        private final long ptr;
        // Position of the next primitive.
        private int primitiveIndex;

        // Creates a sink at an address.
        private BufferSink(long ptr) {
            this.ptr = ptr;
        }

        // Writes each quad of a batch.
        @Override
        public void accept(int[] quads) {
            for (int quad : quads) {
                writePrimitive(this.ptr, this.primitiveIndex++, quad);
            }
        }
    }

    // Writes indices ordered for the camera and rejects a buffer or sort state that does not match the quad count.
    @Override
    public void generateSortedIndexBuffer(ByteBuffer indexBuffer, int quadCount, @Nullable SortState chunkData, float x, float y, float z) {
        if (chunkData instanceof PartitionTree tree) {
            int bufferSize = getIndexBufferSize(quadCount);
            if (indexBuffer.remaining() != bufferSize) {
                throw new IllegalStateException("Given index buffer has " + indexBuffer.remaining() + " remaining bytes but we expected " + bufferSize);
            }
            if (quadCount != tree.quadCount()) {
                throw new IllegalStateException(String.format("Mismatched SortState (%d) vs given quad count (%d)", tree.quadCount(), quadCount));
            }

            var sink = new BufferSink(MemoryUtil.memAddress(indexBuffer));
            tree.order(x, y, z, sink);

            if (sink.primitiveIndex != quadCount) {
                throw new IllegalStateException(String.format("Partition tree wrote %d of %d quads", sink.primitiveIndex, quadCount));
            }
            return;
        }

        int[] order = chunkData != null ? TranslucentSorter.order(chunkData, x, y, z) : null;

        if (order == null) {
            generateSimpleIndexBuffer(indexBuffer, quadCount);
            return;
        }

        if (quadCount != order.length) {
            throw new IllegalStateException(String.format("Mismatched SortState (%d) vs given quad count (%d)", order.length, quadCount));
        }

        generateIndexBuffer(indexBuffer, order);
    }
}
