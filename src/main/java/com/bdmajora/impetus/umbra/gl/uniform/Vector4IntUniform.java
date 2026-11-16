package com.bdmajora.impetus.umbra.gl.uniform;

import org.joml.Vector4i;

import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Four component int vector uniform.
public class Vector4IntUniform extends CachedUniform<Vector4i> {
    // Creates a vector4 int uniform from its location and value.
    public Vector4IntUniform(int location, Supplier<Vector4i> value) {
        super(location, value, new Vector4i());
    }

    // Copies a new value into the cache.
    @Override
    protected void store(Vector4i cached, Vector4i newValue) {
        cached.set(newValue);
    }

    // Uploads the vector.
    @Override
    protected void upload(Vector4i v) {
        LWJGL.glUniform4i(this.location, v.x, v.y, v.z, v.w);
    }
}
