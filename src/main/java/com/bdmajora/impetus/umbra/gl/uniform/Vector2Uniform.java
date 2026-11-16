package com.bdmajora.impetus.umbra.gl.uniform;

import org.joml.Vector2f;

import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Two component float vector uniform.
public class Vector2Uniform extends CachedUniform<Vector2f> {
    // Creates a vector2 uniform from its location and value.
    public Vector2Uniform(int location, Supplier<Vector2f> value) {
        super(location, value, new Vector2f());
    }

    // Copies a new value into the cache.
    @Override
    protected void store(Vector2f cached, Vector2f newValue) {
        cached.set(newValue);
    }

    // Uploads the vector.
    @Override
    protected void upload(Vector2f v) {
        LWJGL.glUniform2f(this.location, v.x, v.y);
    }
}
