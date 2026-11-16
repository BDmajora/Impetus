package com.bdmajora.impetus.umbra.gl.uniform;

import org.joml.Matrix3fc;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Three by three matrix uniform that uploads on change.
public class Matrix3Uniform extends Uniform {
    // Supplier of the matrix.
    private final Supplier<Matrix3fc> value;

    // Creates a matrix3 uniform from its location and value.
    public Matrix3Uniform(int location, Supplier<Matrix3fc> value) {
        super(location);
        this.value = value;
    }

    // Uploads the matrix when it changed.
    @Override
    public void update() {
        Matrix3fc matrix = this.value.get();
        if (matrix == null) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(9);
            matrix.get(buffer);
            LWJGL.glUniformMatrix3fv(this.location, false, buffer);
        }
    }
}
