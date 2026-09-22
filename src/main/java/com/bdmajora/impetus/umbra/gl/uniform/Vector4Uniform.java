package com.bdmajora.impetus.umbra.gl.uniform;

import org.joml.Vector4f;

import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// A vec4 uniform (OptiFine's entityColor, hurt/flash tint plus strength), diffed against the last upload by CachedUniform
public class Vector4Uniform extends CachedUniform<Vector4f> {
    public Vector4Uniform(int location, Supplier<Vector4f> value) {
        super(location, value, new Vector4f());
    }

    @Override
    protected void store(Vector4f cached, Vector4f newValue) {
        cached.set(newValue);
    }

    @Override
    protected void upload(Vector4f v) {
        LWJGL.glUniform4f(this.location, v.x, v.y, v.z, v.w);
    }
}
