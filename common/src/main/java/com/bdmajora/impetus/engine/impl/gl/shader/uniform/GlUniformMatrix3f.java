package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import org.joml.Matrix3f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.Objects;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Three by three float matrix uniform bound to one location in a linked program
public class GlUniformMatrix3f extends GlUniform<Matrix3f> {
    // Number of floats in one three by three matrix
    private static final int ELEMENTS = 9;

    // Creates a matrix uniform at the location and rejects a location below minus one
    public GlUniformMatrix3f(int index) {
        super(index);
    }

    // Copies the matrix in column major order into thread stack memory and uploads it untransposed to the location in the program in use
    @Override
    public void set(Matrix3f value) {
        // Rejects a null matrix
        Objects.requireNonNull(value, "Matrix must not be null");

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(ELEMENTS);
            value.get(buf);

            LWJGL.glUniformMatrix3fv(this.index, false, buf);
        }
    }
}
