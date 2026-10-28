package com.bdmajora.impetus.engine.impl.gl.buffer;

import org.lwjgl.opengl.GL15;

// Usage hints that glBufferData accepts with the GL enum of each hint
public enum GlBufferUsage {
    // Hint for data the application writes once and GL draws from a few times
    STREAM_DRAW(GL15.GL_STREAM_DRAW),
    // Hint for data GL writes once and the application reads a few times
    STREAM_READ(GL15.GL_STREAM_READ),
    // Hint for data GL writes once and GL draws from a few times
    STREAM_COPY(GL15.GL_STREAM_COPY),
    // Hint for data the application writes once and GL draws from many times
    STATIC_DRAW(GL15.GL_STATIC_DRAW),
    // Hint for data GL writes once and the application reads many times
    STATIC_READ(GL15.GL_STATIC_READ),
    // Hint for data GL writes once and GL draws from many times
    STATIC_COPY(GL15.GL_STATIC_COPY),
    // Hint for data the application rewrites often and GL draws from many times
    DYNAMIC_DRAW(GL15.GL_DYNAMIC_DRAW),
    // Hint for data GL rewrites often and the application reads many times
    DYNAMIC_READ(GL15.GL_DYNAMIC_READ),
    // Hint for data GL rewrites often and GL draws from many times
    DYNAMIC_COPY(GL15.GL_DYNAMIC_COPY);

    // GL enum of the usage hint
    private final int id;

    // Stores the GL enum of the usage hint
    GlBufferUsage(int id) {
        this.id = id;
    }

    // Returns the GL enum of the usage hint
    public int getId() {
        return this.id;
    }
}
