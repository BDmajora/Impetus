package com.bdmajora.impetus.umbra.gl.uniform;

import org.joml.Vector3i;

import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Three component int vector uniform.
public class Vector3IntUniform extends CachedUniform<Vector3i> {
    // Creates a vector3 int uniform from its location and value.
    public Vector3IntUniform(int location, Supplier<Vector3i> value) {
        super(location, value, new Vector3i());
    }

    // Copies a new value into the cache.
    @Override
    protected void store(Vector3i cached, Vector3i newValue) {
        cached.set(newValue);
    }

    // Uploads the vector.
    @Override
    protected void upload(Vector3i v) {
        LWJGL.glUniform3i(this.location, v.x, v.y, v.z);
    }
}
