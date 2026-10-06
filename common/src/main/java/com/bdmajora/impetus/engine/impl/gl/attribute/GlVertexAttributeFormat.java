package com.bdmajora.impetus.engine.impl.gl.attribute;

import org.lwjgl.opengl.GL20;


// A vertex attribute's GL type enum plus its component size in bytes; a record so the size travels with the type and strides cannot drift
public record GlVertexAttributeFormat(int typeId, int size) {
    public static final GlVertexAttributeFormat FLOAT = new GlVertexAttributeFormat(GL20.GL_FLOAT, 4);
    public static final GlVertexAttributeFormat SHORT = new GlVertexAttributeFormat(GL20.GL_SHORT, 2);
    public static final GlVertexAttributeFormat UNSIGNED_SHORT = new GlVertexAttributeFormat(GL20.GL_UNSIGNED_SHORT, 2);
    public static final GlVertexAttributeFormat BYTE = new GlVertexAttributeFormat(GL20.GL_BYTE, 1);
    public static final GlVertexAttributeFormat UNSIGNED_BYTE = new GlVertexAttributeFormat(GL20.GL_UNSIGNED_BYTE, 1);
    public static final GlVertexAttributeFormat UNSIGNED_INT = new GlVertexAttributeFormat(GL20.GL_UNSIGNED_INT, 4);
}
