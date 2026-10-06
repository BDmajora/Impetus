package com.bdmajora.impetus.engine.impl.gl.tessellation;

import org.lwjgl.opengl.GL32;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;


public enum GlIndexType {
    UNSIGNED_BYTE(GL32.GL_UNSIGNED_BYTE, 1),
    UNSIGNED_SHORT(GL32.GL_UNSIGNED_SHORT, 2),
    UNSIGNED_INT(GL32.GL_UNSIGNED_INT, 4);

    private final int id;
    private final int stride;

    GlIndexType(int id, int stride) {
        this.id = id;
        this.stride = stride;
    }

    // GL type constant
    public int getFormatId() {
        return this.id;
    }

    // Bytes per index
    public int getStride() {
        return this.stride;
    }
}
