package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Integer uniform bound to one location in a linked program
public class GlUniformInt extends GlUniform<Integer> {
    // Creates an integer uniform at the location and rejects a location below minus one
    public GlUniformInt(int index) {
        super(index);
    }

    // Unboxes the value and uploads it to the location in the program in use
    @Override
    public void set(Integer value) {
        this.setInt(value);
    }

    // Uploads the value to the location in the program in use
    public void setInt(int value) {
        LWJGL.glUniform1i(this.index, value);
    }
}
