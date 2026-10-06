package com.bdmajora.impetus.engine.impl.gl.buffer;

import org.lwjgl.opengl.GL20;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;


public enum GlBufferUsage {
    STREAM_DRAW(GL20.GL_STREAM_DRAW),
    STREAM_READ(GL20.GL_STREAM_READ),
    STREAM_COPY(GL20.GL_STREAM_COPY),
    STATIC_DRAW(GL20.GL_STATIC_DRAW),
    STATIC_READ(GL20.GL_STATIC_READ),
    STATIC_COPY(GL20.GL_STATIC_COPY),
    DYNAMIC_DRAW(GL20.GL_DYNAMIC_DRAW),
    DYNAMIC_READ(GL20.GL_DYNAMIC_READ),
    DYNAMIC_COPY(GL20.GL_DYNAMIC_COPY);

    private final int id;

    GlBufferUsage(int id) {
        this.id = id;
    }

    // GL usage constant
    public int getId() {
        return this.id;
    }
}
