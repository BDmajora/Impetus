package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Objects;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Float array uniform bound to one location in a linked program
public class GlUniformFloatArray extends GlUniform<float[]> {
    // Creates an array uniform at the location and rejects a location below minus one
    public GlUniformFloatArray(int index) {
        super(index);
    }

    // Uploads every element of the array to the location in the program in use
    @Override
    public void set(float[] value) {
        // Rejects a null array
        Objects.requireNonNull(value, "Values must not be null");

        LWJGL.glUniform1fv(this.index, value);
    }

    // Uploads the elements between the position and the limit of the buffer to the location in the program in use
    public void set(FloatBuffer value) {
        // Rejects a null buffer
        Objects.requireNonNull(value, "Buffer must not be null");

        // Rejects a buffer outside native memory
        if (!value.isDirect()) {
            throw new IllegalArgumentException("Buffer must be a direct buffer");
        }

        // Rejects a buffer whose byte order differs from the native byte order
        if (value.order() != ByteOrder.nativeOrder()) {
            throw new IllegalArgumentException("Buffer must use the native byte order");
        }

        LWJGL.glUniform1fv(this.index, value);
    }
}
