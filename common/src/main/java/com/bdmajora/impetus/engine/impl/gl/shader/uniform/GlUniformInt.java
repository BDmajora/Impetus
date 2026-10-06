package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import org.lwjgl.opengl.GL30;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;


public class GlUniformInt extends GlUniform<Integer> {
    public GlUniformInt(int index) {
        super(index);
    }

    // Boxed form
    @Override
    public void set(Integer value) {
        this.setInt(value);
    }

    // glUniform1i
    public void setInt(int value) {
        LWJGL.glUniform1i(this.index, value);
    }
}
