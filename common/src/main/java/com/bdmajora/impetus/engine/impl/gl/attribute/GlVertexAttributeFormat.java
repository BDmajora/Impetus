package com.bdmajora.impetus.engine.impl.gl.attribute;

import org.lwjgl.opengl.GL20;


// GL component type of a vertex attribute with the size of one component in bytes
public record GlVertexAttributeFormat(int typeId, int size) {
    // Format of a 32 bit floating point component
    public static final GlVertexAttributeFormat FLOAT = new GlVertexAttributeFormat(GL20.GL_FLOAT, 4);
    // Format of a signed 16 bit integer component
    public static final GlVertexAttributeFormat SHORT = new GlVertexAttributeFormat(GL20.GL_SHORT, 2);
    // Format of an unsigned 16 bit integer component
    public static final GlVertexAttributeFormat UNSIGNED_SHORT = new GlVertexAttributeFormat(GL20.GL_UNSIGNED_SHORT, 2);
    // Format of a signed 8 bit integer component
    public static final GlVertexAttributeFormat BYTE = new GlVertexAttributeFormat(GL20.GL_BYTE, 1);
    // Format of an unsigned 8 bit integer component
    public static final GlVertexAttributeFormat UNSIGNED_BYTE = new GlVertexAttributeFormat(GL20.GL_UNSIGNED_BYTE, 1);
    // Format of an unsigned 32 bit integer component
    public static final GlVertexAttributeFormat UNSIGNED_INT = new GlVertexAttributeFormat(GL20.GL_UNSIGNED_INT, 4);

    // Rejects a component size that is zero or negative
    public GlVertexAttributeFormat {
        if (size <= 0) {
            throw new IllegalArgumentException("Format size must be positive");
        }
    }
}
