package com.bdmajora.impetus.engine.impl.gl.tessellation;

import org.lwjgl.opengl.GL32;

// Index element types with their GL enum and byte size.
public enum GlIndexType {
    UNSIGNED_BYTE(GL32.GL_UNSIGNED_BYTE, 1),
    UNSIGNED_SHORT(GL32.GL_UNSIGNED_SHORT, 2),
    UNSIGNED_INT(GL32.GL_UNSIGNED_INT, 4);

    // GL enum of the index type.
    private final int id;
    // Bytes of one index.
    private final int stride;

    // Creates an index type.
    GlIndexType(int id, int stride) {
        this.id = id;
        this.stride = stride;
    }

    // Returns the GL enum of the index type.
    public int getFormatId() {
        return this.id;
    }

    // Returns the bytes of one index.
    public int getStride() {
        return this.stride;
    }
}
