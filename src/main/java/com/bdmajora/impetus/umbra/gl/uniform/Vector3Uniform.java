package com.bdmajora.impetus.umbra.gl.uniform;

import org.joml.Vector3f;

import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// A vec3 uniform (camera position, sun/moon vectors, fog colour), diffed against the last upload by CachedUniform
public class Vector3Uniform extends CachedUniform<Vector3f> {
    public Vector3Uniform(int location, Supplier<Vector3f> value) {
        super(location, value, new Vector3f());
    }

    @Override
    protected void store(Vector3f cached, Vector3f newValue) {
        cached.set(newValue);
    }

    @Override
    protected void upload(Vector3f v) {
        LWJGL.glUniform3f(this.location, v.x, v.y, v.z);
    }
}
