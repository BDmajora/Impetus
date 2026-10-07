package com.bdmajora.impetus.engine.impl.render.chunk.vertex.builder;

import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.SortState;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.TranslucentQuadRecorder;
import org.jetbrains.annotations.Nullable;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

public class ChunkMeshBufferBuilder {
    private final ChunkVertexEncoder encoder;
    private final int stride;

    private final int initialCapacity;
    private final TranslucentQuadRecorder analyzer;

    private ByteBuffer buffer;
    private int count;
    private int capacity;
    private int sectionIndex;

    public ChunkMeshBufferBuilder(ChunkVertexEncoder encoder, int stride, int initialCapacity, boolean collectSortState) {
        this.encoder = encoder;
        this.stride = stride;

        this.buffer = null;

        this.capacity = 0;
        this.initialCapacity = initialCapacity;

        this.analyzer = collectSortState ? new TranslucentQuadRecorder() : null;
    }

    // Encodes one quad, growing the buffer and feeding the translucency analyzer
    public void push(ChunkVertexEncoder.Vertex[] vertices, Material material) {
        if (this.encoder.supportsBilinearCorrection()) {
            postprocessVertices(vertices);
        }

        var vertexStart = this.count * this.stride;
        var vertexSize = vertices.length * this.stride;

        if (vertexStart + vertexSize >= this.capacity) {
            this.grow(vertexSize);
        }

        long ptr = MemoryUtil.memAddress(this.buffer, vertexStart);

        // One walk for both; the encoder never mutates the vertex, so the analyzer can read it first
        var analyzer = this.analyzer;

        for (ChunkVertexEncoder.Vertex vertex : vertices) {
            if (analyzer != null) {
                analyzer.capture(vertex);
            }

            ptr = this.encoder.write(ptr, material, vertex, this.sectionIndex);
        }

        this.count += vertices.length;
    }

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
        // TODO can likely do SWAR tricks if performance is an issue
        int factor = encodeBilinearCorrection(color3, color1, color0, color2, 0)
                | encodeBilinearCorrection(color3, color1, color0, color2, 8) << 8
                | encodeBilinearCorrection(color3, color1, color0, color2, 16) << 16
                | encodeBilinearCorrection(color3, color1, color0, color2, 24) << 24;

        for (var vertex : vertices) {
            vertex.rdhFactor = factor;
        }
    }

    private static int encodeBilinearCorrection(int positive0, int positive1, int negative0, int negative1, int shift) {
        int factor = ((positive0 >> shift) & 0xFF) + ((positive1 >> shift) & 0xFF)
                - ((negative0 >> shift) & 0xFF) - ((negative1 >> shift) & 0xFF);
        return Math.round(factor * (127.0F / 510.0F)) & 0xFF;
    }

    private void grow(int bytesNeeded) {
        // Grow by a factor of 2, or by however many bytes more we need, whichever is larger.
        int newCapacity = Math.max(this.capacity * 2, this.capacity + bytesNeeded);
        // Ensure we allocate at least initialCapacity bytes
        newCapacity = Math.max(newCapacity, this.initialCapacity);

        this.buffer = MemoryUtil.memRealloc(this.buffer, newCapacity);
        this.capacity = newCapacity;
    }

    // Resets for a new section
    public void start(int sectionIndex) {
        this.count = 0;
        this.sectionIndex = sectionIndex;
        if(this.analyzer != null) {
            this.analyzer.clear();
        }
    }

    // The analyzer's classification of what was pushed
    @Nullable
    public SortState getSortState() {
        return this.analyzer != null ? this.analyzer.getSortState() : null;
    }

    // Clears the analyzer
    public void resetSortState() {
        if (this.analyzer != null) {
            this.analyzer.clear();
        }
    }

    // Frees the native buffer
    public void destroy() {
        if (this.buffer != null) {
            MemoryUtil.memFree(this.buffer);
        }

        this.buffer = null;
        this.capacity = 0;

        this.resetSortState();
    }

    // No vertices
    public boolean isEmpty() {
        return this.count == 0;
    }

    // A view over what was written
    public ByteBuffer slice() {
        if (this.isEmpty()) {
            throw new IllegalStateException("No vertex data in buffer");
        }

        return MemoryUtil.memSlice(this.buffer, 0, this.stride * this.count);
    }

    // Vertices written
    public int count() {
        return this.count;
    }
}
