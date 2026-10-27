package com.bdmajora.impetus.engine.impl.gl.array;

import com.bdmajora.impetus.engine.impl.gl.GlObject;
import com.bdmajora.impetus.lwjgl.LWJGLServiceProvider;

// Owner of one vertex array object name from the LWJGL service
public class GlVertexArray extends GlObject {
    // Reserved name that binds no vertex array
    public static final int NULL_ARRAY_ID = 0;

    // Generates a vertex array name and rejects a zero name from the driver
    public GlVertexArray() {
        int name = LWJGLServiceProvider.LWJGL.glGenVertexArrays();

        if (name == NULL_ARRAY_ID) {
            throw new IllegalStateException("Failed to generate a vertex array");
        }

        this.setHandle(name);
    }

    // Deletes the vertex array name through the LWJGL service
    @Override
    protected void destroyInternal() {
        LWJGLServiceProvider.LWJGL.glDeleteVertexArrays(this.handle());
    }
}
