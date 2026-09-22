package com.bdmajora.impetus.umbra.gl.uniform;

import org.joml.Vector2i;

import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// An ivec2 uniform (OptiFine's eyeBrightness), diffed against the last upload by CachedUniform
public class Vector2IntUniform extends CachedUniform<Vector2i> {
    public Vector2IntUniform(int location, Supplier<Vector2i> value) {
        super(location, value, new Vector2i());
    }

    @Override
    protected void store(Vector2i cached, Vector2i newValue) {
        cached.set(newValue);
    }

    @Override
    protected void upload(Vector2i v) {
        LWJGL.glUniform2i(this.location, v.x, v.y);
    }
}
