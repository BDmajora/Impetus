package com.bdmajora.impetus.umbra.gl.uniform;

import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// A mat4 uniform (gbuffer/shadow model-view and projection plus inverses); does NOT cache and diff, since comparing 16 floats rarely beats the upload and these change almost every frame anyway
public class MatrixUniform extends Uniform {
    private final Supplier<Matrix4fc> value;

    public MatrixUniform(int location, Supplier<Matrix4fc> value) {
        super(location);
        this.value = value;
    }

    // Uploads unconditionally; see the class comment
    @Override
    public void update() {
        Matrix4fc matrix = this.value.get();
        // A supplier can legitimately have nothing yet (an inverse not captured this frame), and keeping the previous value beats uploading garbage
        if (matrix == null) {
            return;
        }
        // Thread-local stack rather than a field or fresh allocation: the buffer lives only for this call and try-with-resources pops it even if the upload throws
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            matrix.get(buffer);
            // false = do not transpose; JOML already stores column-major, which is what GL expects
            LWJGL.glUniformMatrix4fv(this.location, false, buffer);
        }
    }
}
