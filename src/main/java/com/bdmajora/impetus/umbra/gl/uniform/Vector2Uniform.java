package com.bdmajora.impetus.umbra.gl.uniform;

import org.joml.Vector2f;

import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// A vec2 uniform (screen/texture sizes, two-component pack constants), diffed against the last upload by CachedUniform
public class Vector2Uniform extends CachedUniform<Vector2f> {
    public Vector2Uniform(int location, Supplier<Vector2f> value) {
        super(location, value, new Vector2f());
    }

    @Override
    protected void store(Vector2f cached, Vector2f newValue) {
        cached.set(newValue);
    }

    @Override
    protected void upload(Vector2f v) {
        LWJGL.glUniform2f(this.location, v.x, v.y);
    }
}
