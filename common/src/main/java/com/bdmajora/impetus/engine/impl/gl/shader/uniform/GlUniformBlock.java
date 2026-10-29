package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import org.lwjgl.opengl.GL32;
import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;

// Uniform block of a linked program assigned to one uniform buffer binding point
public class GlUniformBlock {
    // Uniform buffer binding point of the block
    private final int binding;

    // Stores the binding point and rejects a negative binding point
    public GlUniformBlock(int uniformBlockBinding) {
        if (uniformBlockBinding < 0) {
            throw new IllegalArgumentException("Uniform block binding must not be negative");
        }

        this.binding = uniformBlockBinding;
    }

    // Binds the whole buffer to the binding point and rejects a null or deleted buffer before the GL call
    public void bindBuffer(GlBuffer buffer) {
        LWJGL.glBindBufferBase(GL32.GL_UNIFORM_BUFFER, this.binding, buffer.handle());
    }
}
