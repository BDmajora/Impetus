package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Four component float vector uniform bound to one location in a linked program
public class GlUniformFloat4v extends GlUniform<float[]> {
    // Creates a vector uniform at the location and rejects a location below minus one
    public GlUniformFloat4v(int index) {
        super(index);
    }

    // Uploads the four elements of the array to the location in the program in use
    @Override
    public void set(float[] value) {
        // Rejects a null array or an array whose length is not 4
        if (value.length != 4) {
            throw new IllegalArgumentException("value.length != 4");
        }

        LWJGL.glUniform4f(this.index, value[0], value[1], value[2], value[3]);
    }
}
