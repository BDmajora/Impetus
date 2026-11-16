package com.bdmajora.impetus.umbra.gl.uniform;

import org.joml.Vector3f;

import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Three component float vector uniform.
public class Vector3Uniform extends CachedUniform<Vector3f> {
    // Creates a vector3 uniform from its location and value.
    public Vector3Uniform(int location, Supplier<Vector3f> value) {
        super(location, value, new Vector3f());
    }

    // Copies a new value into the cache.
    @Override
    protected void store(Vector3f cached, Vector3f newValue) {
        cached.set(newValue);
    }

    // Uploads the vector.
    @Override
    protected void upload(Vector3f v) {
        LWJGL.glUniform3f(this.location, v.x, v.y, v.z);
    }
}
