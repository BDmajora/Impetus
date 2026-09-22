package com.bdmajora.impetus.umbra.gl.uniform;

import org.joml.Vector3i;

import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// An ivec3 uniform for Iris's cameraPositionInt, diffed against the last upload by CachedUniform
public class Vector3IntUniform extends CachedUniform<Vector3i> {
    public Vector3IntUniform(int location, Supplier<Vector3i> value) {
        super(location, value, new Vector3i());
    }

    @Override
    protected void store(Vector3i cached, Vector3i newValue) {
        cached.set(newValue);
    }

    @Override
    protected void upload(Vector3i v) {
        LWJGL.glUniform3i(this.location, v.x, v.y, v.z);
    }
}
