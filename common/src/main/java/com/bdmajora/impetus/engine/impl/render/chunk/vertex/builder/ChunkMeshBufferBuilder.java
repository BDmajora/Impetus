package com.bdmajora.impetus.engine.impl.render.chunk.vertex.builder;

import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.SortState;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.TranslucentQuadRecorder;
import org.jetbrains.annotations.Nullable;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.lwjgl.system.MemoryUtil;

// Collects the encoded vertices of a mesh in native memory.
public class ChunkMeshBufferBuilder {
    // Encoder of the vertices.
    private final ChunkVertexEncoder encoder;
    // Bytes of one vertex.
    private final int stride;

    // Smallest size of the buffer.
    private final int initialCapacity;
    // Recorder of the sorting data or null.
    private final TranslucentQuadRecorder analyzer;

    // Native buffer or null.
    private ByteBuffer buffer;
    // Number of vertices written.
    private int count;
    // Size of the buffer in bytes.
    private int capacity;
    // Index of the section being built.
    private int sectionIndex;

    // Creates a builder and rejects a null encoder or a stride that is not positive.
    public ChunkMeshBufferBuilder(ChunkVertexEncoder encoder, int stride, int initialCapacity, boolean collectSortState) {
        if (stride <= 0 || initialCapacity < 0) {
            throw new IllegalArgumentException("Invalid stride " + stride + " or capacity " + initialCapacity);
        }
        this.encoder = Objects.requireNonNull(encoder, "encoder");
        this.stride = stride;

        this.buffer = null;

        this.capacity = 0;
        this.initialCapacity = initialCapacity;

        this.analyzer = collectSortState ? new TranslucentQuadRecorder() : null;
    }

    // Encodes one quad and grows the buffer and records the sorting data and ignores an empty quad.
    public void push(ChunkVertexEncoder.Vertex[] vertices, Material material) {
        if (vertices.length == 0) {
            return;
        }
        if (this.encoder.supportsBilinearCorrection()) {
            postprocessVertices(vertices);
        }

        var vertexStart = Math.multiplyExact(this.count, this.stride);
        var vertexSize = Math.multiplyExact(vertices.length, this.stride);

        if (Math.addExact(vertexStart, vertexSize) > this.capacity) {
            this.grow(vertexSize);
        }

        long ptr = MemoryUtil.memAddress(this.buffer, vertexStart);

        var analyzer = this.analyzer;

        for (ChunkVertexEncoder.Vertex vertex : vertices) {
            ptr = this.encoder.write(ptr, material, vertex, this.sectionIndex);
        }

        if (analyzer != null) {
            for (ChunkVertexEncoder.Vertex vertex : vertices) {
                analyzer.capture(vertex);
            }
        }

        this.count += vertices.length;
    }

    // Calculates the bilinear correction of a quad.
    private static void postprocessVertices(ChunkVertexEncoder.Vertex[] vertices) {
        if (vertices.length != 4) {
            for (var vertex : vertices) {
                vertex.rdhFactor = 0;
            }
            return;
        }

        int color0 = vertices[0].color;
        int color1 = vertices[1].color;
        int color2 = vertices[2].color;
        int color3 = vertices[3].color;
        int factor = encodeBilinearCorrection(color3, color1, color0, color2, 0)
                | encodeBilinearCorrection(color3, color1, color0, color2, 8) << 8
                | encodeBilinearCorrection(color3, color1, color0, color2, 16) << 16
                | encodeBilinearCorrection(color3, color1, color0, color2, 24) << 24;

        for (var vertex : vertices) {
            vertex.rdhFactor = factor;
        }
    }

    // Returns the correction of one color channel.
    private static int encodeBilinearCorrection(int positive0, int positive1, int negative0, int negative1, int shift) {
        int factor = ((positive0 >> shift) & 0xFF) + ((positive1 >> shift) & 0xFF)
                - ((negative0 >> shift) & 0xFF) - ((negative1 >> shift) & 0xFF);
        return Math.round(factor * (127.0F / 510.0F)) & 0xFF;
    }

    // Grows the buffer to hold more bytes and rejects a failed allocation.
    private void grow(int bytesNeeded) {
        int newCapacity = Math.max((int) Math.min(Integer.MAX_VALUE, this.capacity * 2L), Math.addExact(this.capacity, bytesNeeded));
        newCapacity = Math.max(newCapacity, this.initialCapacity);

        ByteBuffer grown = MemoryUtil.memRealloc(this.buffer, newCapacity);
        // Keeps the old buffer when the allocation fails
        if (grown == null) {
            throw new OutOfMemoryError("Failed to grow the mesh buffer to " + newCapacity + " bytes");
        }
        this.buffer = grown;
        this.capacity = newCapacity;
    }

    // Starts a new section.
    public void start(int sectionIndex) {
        this.count = 0;
        this.sectionIndex = sectionIndex;
        if (this.analyzer != null) {
            this.analyzer.clear();
        }
    }

    // Returns the sort state of the written quads or null.
    @Nullable
    public SortState getSortState() {
        return this.analyzer != null ? this.analyzer.getSortState() : null;
    }

    // Clears the sorting data.
    public void resetSortState() {
        if (this.analyzer != null) {
            this.analyzer.clear();
        }
    }

    // Frees the buffer and clears the state.
    public void destroy() {
        if (this.buffer != null) {
            MemoryUtil.memFree(this.buffer);
        }

        this.buffer = null;
        this.capacity = 0;
        this.count = 0;

        this.resetSortState();
    }

    // Returns whether no vertex was written.
    public boolean isEmpty() {
        return this.count == 0;
    }

    // Returns a view of the written vertices and rejects an empty buffer.
    public ByteBuffer slice() {
        if (this.isEmpty()) {
            throw new IllegalStateException("No vertex data in buffer");
        }

        return MemoryUtil.memSlice(this.buffer, 0, this.stride * this.count);
    }

    // Returns the number of vertices.
    public int count() {
        return this.count;
    }
}
