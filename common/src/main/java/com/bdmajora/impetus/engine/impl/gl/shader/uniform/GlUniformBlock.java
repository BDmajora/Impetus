package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import org.lwjgl.opengl.GL32;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;

public class GlUniformBlock {
    private final int binding;

    public GlUniformBlock(int uniformBlockBinding) {
        this.binding = uniformBlockBinding;
    }

    // glBindBufferBase at this block's binding point
    public void bindBuffer(GlBuffer buffer) {
        LWJGL.glBindBufferBase(GL32.GL_UNIFORM_BUFFER, this.binding, buffer.handle());
    }
}
