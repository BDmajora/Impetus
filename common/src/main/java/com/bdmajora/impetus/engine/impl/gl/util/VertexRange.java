package com.bdmajora.impetus.engine.impl.gl.util;

// Run of vertices given by a first vertex and a count.
public record VertexRange(int vertexStart, int vertexCount) {
    // Rejects a negative start or count.
    public VertexRange {
        if ((vertexStart | vertexCount) < 0) {
            throw new IllegalArgumentException("Negative vertex range " + vertexStart + " " + vertexCount);
        }
    }
}
