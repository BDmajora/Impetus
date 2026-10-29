package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Float uniform bound to one location in a linked program
public class GlUniformFloat extends GlUniform<Float> {
    // Creates a float uniform at the location and rejects a location below minus one
    public GlUniformFloat(int index) {
        super(index);
    }

    // Unboxes the value and uploads it to the location in the program in use
    @Override
    public void set(Float value) {
        this.setFloat(value);
    }

    // Uploads the value to the location in the program in use
    public void setFloat(float value) {
        LWJGL.glUniform1f(this.index, value);
    }
}
