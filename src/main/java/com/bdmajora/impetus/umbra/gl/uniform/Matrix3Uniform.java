package com.bdmajora.impetus.umbra.gl.uniform;

import org.joml.Matrix3fc;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

public class Matrix3Uniform extends Uniform {
    private final Supplier<Matrix3fc> value;

    public Matrix3Uniform(int location, Supplier<Matrix3fc> value) {
        super(location);
        this.value = value;
    }

    // Uploads unconditionally; diffing nine floats is not cheaper than the upload
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
