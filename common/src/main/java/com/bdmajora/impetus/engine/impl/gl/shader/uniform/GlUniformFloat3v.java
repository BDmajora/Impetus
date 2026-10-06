package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import org.lwjgl.opengl.GL30;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;


public class GlUniformFloat3v extends GlUniform<float[]> {
    public GlUniformFloat3v(int index) {
        super(index);
    }

    // Array form; must be length 3
    @Override
    public void set(float[] value) {
        if (value.length != 3) {
            throw new IllegalArgumentException("value.length != 3");
        }

        LWJGL.glUniform3fv(this.index, value);
    }

    // glUniform3f
    public void set(float x, float y, float z) {
        LWJGL.glUniform3f(this.index, x, y, z);
    }
}
