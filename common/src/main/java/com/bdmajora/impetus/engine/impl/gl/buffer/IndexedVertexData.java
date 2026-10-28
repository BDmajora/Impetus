package com.bdmajora.impetus.engine.impl.gl.buffer;

import com.bdmajora.impetus.engine.impl.gl.attribute.GlVertexFormat;
import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;

import java.util.Objects;

// Vertex data and index data in native memory with the format that describes the vertices
public record IndexedVertexData(GlVertexFormat vertexFormat,
                                NativeBuffer vertexBuffer,
                                NativeBuffer indexBuffer) {
    // Creates the record and rejects a null format or a null buffer
    public IndexedVertexData {
        Objects.requireNonNull(vertexFormat, "Vertex format must not be null");
        Objects.requireNonNull(vertexBuffer, "Vertex buffer must not be null");
        Objects.requireNonNull(indexBuffer, "Index buffer must not be null");
    }

    // Frees the vertex buffer and then the index buffer and frees a buffer that fills both slots once
    public void delete() {
        NativeBuffer vertices = this.vertexBuffer;
        NativeBuffer indices = this.indexBuffer;

        try {
            vertices.free();
        } catch (Throwable failure) {
            // Frees the index buffer after a failed vertex free and attaches a second failure as suppressed
            if (indices != vertices) {
                try {
                    indices.free();
                } catch (Throwable second) {
                    if (second != failure) {
                        failure.addSuppressed(second);
                    }
                }
            }

            throw failure;
        }

        if (indices != vertices) {
            indices.free();
        }
    }
}
