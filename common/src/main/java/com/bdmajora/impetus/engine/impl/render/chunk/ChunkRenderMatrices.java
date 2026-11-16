package com.bdmajora.impetus.engine.impl.render.chunk;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.nio.BufferUnderflowException;
import java.nio.FloatBuffer;
import java.util.Objects;

// Projection and model view matrices of a chunk pass.
public record ChunkRenderMatrices(Matrix4fc projection, Matrix4fc modelView) {
    // Rejects null matrices.
    public ChunkRenderMatrices {
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(modelView, "modelView");
    }

    // Creates matrices from buffers without consuming them and rejects a buffer with fewer than sixteen floats.
    public ChunkRenderMatrices(FloatBuffer projection, FloatBuffer modelView) {
        this(read(projection), read(modelView));
    }

    // Reads a matrix from the remaining floats of a buffer.
    private static Matrix4f read(FloatBuffer buffer) {
        if (buffer.remaining() < 16) {
            throw new BufferUnderflowException();
        }

        return new Matrix4f(buffer);
    }
}
