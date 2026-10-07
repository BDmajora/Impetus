package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import org.joml.Matrix3f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

public class GlUniformMatrix3f extends GlUniform<Matrix3f> {
    public GlUniformMatrix3f(int index) {
        super(index);
    }

    // glUniformMatrix3fv via a scratch buffer
    @Override
    public void set(Matrix3f value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.callocFloat(12);
            value.get(buf);

            LWJGL.glUniformMatrix3fv(this.index, false, buf);
        }
    }
}
