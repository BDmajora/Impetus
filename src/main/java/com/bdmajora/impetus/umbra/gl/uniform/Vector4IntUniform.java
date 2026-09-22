package com.bdmajora.impetus.umbra.gl.uniform;

import org.joml.Vector4i;

import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// An ivec4 uniform, diffed against the last upload by CachedUniform
public class Vector4IntUniform extends CachedUniform<Vector4i> {
    public Vector4IntUniform(int location, Supplier<Vector4i> value) {
        super(location, value, new Vector4i());
    }

    @Override
    protected void store(Vector4i cached, Vector4i newValue) {
        cached.set(newValue);
    }

    @Override
    protected void upload(Vector4i v) {
        LWJGL.glUniform4i(this.location, v.x, v.y, v.z, v.w);
    }
}
