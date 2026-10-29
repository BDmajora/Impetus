package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Three component float vector uniform bound to one location in a linked program
public class GlUniformFloat3v extends GlUniform<float[]> {
    // Creates a vector uniform at the location and rejects a location below minus one
    public GlUniformFloat3v(int index) {
        super(index);
    }

    // Uploads the three elements of the array to the location in the program in use
    @Override
    public void set(float[] value) {
        // Rejects a null array or an array whose length is not 3
        if (value.length != 3) {
            throw new IllegalArgumentException("value.length != 3");
        }

        LWJGL.glUniform3f(this.index, value[0], value[1], value[2]);
    }

    // Uploads the three components to the location in the program in use
    public void set(float x, float y, float z) {
        LWJGL.glUniform3f(this.index, x, y, z);
    }
}
