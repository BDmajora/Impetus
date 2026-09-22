package com.bdmajora.impetus.engine.impl.render.chunk.vertex.builder;

import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.TranslucentQuadAnalyzer;
import org.jetbrains.annotations.Nullable;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;
import java.nio.ByteBuffer;

public class ChunkMeshBufferBuilder {
    private final ChunkVertexEncoder encoder;
    private final int stride;

    private final int initialCapacity;
    private final TranslucentQuadAnalyzer analyzer;

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

        this.analyzer = collectSortState ? new TranslucentQuadAnalyzer() : null;
    }

    // Encodes one quad, growing the buffer and feeding the translucency analyzer
    public void push(ChunkVertexEncoder.Vertex[] vertices, Material material) {
        var vertexStart = this.count * this.stride;
        var vertexSize = vertices.length * this.stride;

        if (vertexStart + vertexSize >= this.capacity) {
            this.grow(vertexSize);
        }

        long ptr = LWJGL.memAddress(this.buffer, vertexStart);

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

    // Doubles until the quad fits
    private void grow(int bytesNeeded) {
        // Grow by a factor of 2, or by however many bytes more we need, whichever is larger.
        int newCapacity = Math.max(this.capacity * 2, this.capacity + bytesNeeded);
        // Ensure we allocate at least initialCapacity bytes
        newCapacity = Math.max(newCapacity, this.initialCapacity);

        this.buffer = LWJGL.memRealloc(this.buffer, newCapacity);
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
    public TranslucentQuadAnalyzer.SortState getSortState() {
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
            LWJGL.memFree(this.buffer);
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

        return LWJGL.memSlice(this.buffer, 0, this.stride * this.count);
    }

    // Vertices written
    public int count() {
        return this.count;
    }
}
