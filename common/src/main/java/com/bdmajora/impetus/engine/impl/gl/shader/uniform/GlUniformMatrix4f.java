package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.Objects;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Four by four float matrix uniform bound to one location in a linked program
public class GlUniformMatrix4f extends GlUniform<Matrix4fc> {
    // Number of floats in one four by four matrix
    private static final int ELEMENTS = 16;

    // Creates a matrix uniform at the location and rejects a location below minus one
    public GlUniformMatrix4f(int index) {
        super(index);
    }

    // Copies the matrix in column major order into thread stack memory and uploads it untransposed to the location in the program in use
    @Override
    public void set(Matrix4fc value) {
        // Rejects a null matrix
        Objects.requireNonNull(value, "Matrix must not be null");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(ELEMENTS);
            value.get(buf);

            LWJGL.glUniformMatrix4fv(this.index, false, buf);
        }
    }
}
