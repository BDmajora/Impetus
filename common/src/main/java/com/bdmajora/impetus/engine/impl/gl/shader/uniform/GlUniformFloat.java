package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import org.lwjgl.opengl.GL30;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;


public class GlUniformFloat extends GlUniform<Float> {
    public GlUniformFloat(int index) {
        super(index);
    }

    // Boxed form
    @Override
    public void set(Float value) {
        this.setFloat(value);
    }

    // glUniform1f
    public void setFloat(float value) {
        LWJGL.glUniform1f(this.index, value);
    }
}
