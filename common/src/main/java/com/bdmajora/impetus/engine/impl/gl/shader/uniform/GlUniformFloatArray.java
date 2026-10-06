package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import com.bdmajora.impetus.lwjgl.MemoryStack;
import org.lwjgl.opengl.GL30;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;


import java.nio.FloatBuffer;

public class GlUniformFloatArray extends GlUniform<float[]> {
    public GlUniformFloatArray(int index) {
        super(index);
    }

    // glUniform1fv from an array
    @Override
    public void set(float[] value) {
        try (MemoryStack stack = LWJGL.stackPush()) {
            FloatBuffer buf = stack.callocFloat(value.length);
            buf.put(value);

            LWJGL.glUniform1fv(this.index, buf);
        }
    }

    // glUniform1fv from a buffer
    public void set(FloatBuffer value) {
        LWJGL.glUniform1fv(this.index, value);
    }
}
