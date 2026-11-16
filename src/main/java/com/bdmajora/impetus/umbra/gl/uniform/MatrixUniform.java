package com.bdmajora.impetus.umbra.gl.uniform;

import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Four by four matrix uniform that uploads on change.
public class MatrixUniform extends Uniform {
    // Supplier of the matrix.
    private final Supplier<Matrix4fc> value;

    // Creates a matrix uniform from its location and value.
    public MatrixUniform(int location, Supplier<Matrix4fc> value) {
        super(location);
        this.value = value;
    }

    // Uploads the matrix when it changed.
    @Override
    public void update() {
        Matrix4fc matrix = this.value.get();
        if (matrix == null) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            matrix.get(buffer);
            LWJGL.glUniformMatrix4fv(this.location, false, buffer);
        }
    }
}
