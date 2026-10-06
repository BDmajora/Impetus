package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import org.lwjgl.opengl.GL30;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import org.joml.Matrix4fc;
import com.bdmajora.impetus.lwjgl.MemoryStack;

import java.nio.FloatBuffer;

public class GlUniformMatrix4f extends GlUniform<Matrix4fc>  {
    public GlUniformMatrix4f(int index) {
        super(index);
    }

    // glUniformMatrix4fv via a scratch buffer
    @Override
    public void set(Matrix4fc value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.callocFloat(16);
            value.get(buf);

            LWJGL.glUniformMatrix4fv(this.index, false, buf);
        }
    }
}
