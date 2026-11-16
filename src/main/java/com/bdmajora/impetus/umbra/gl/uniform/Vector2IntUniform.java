package com.bdmajora.impetus.umbra.gl.uniform;

import org.joml.Vector2i;

import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Two component int vector uniform.
public class Vector2IntUniform extends CachedUniform<Vector2i> {
    // Creates a vector2 int uniform from its location and value.
    public Vector2IntUniform(int location, Supplier<Vector2i> value) {
        super(location, value, new Vector2i());
    }

    // Copies a new value into the cache.
    @Override
    protected void store(Vector2i cached, Vector2i newValue) {
        cached.set(newValue);
    }

    // Uploads the vector.
    @Override
    protected void upload(Vector2i v) {
        LWJGL.glUniform2i(this.location, v.x, v.y);
    }
}
