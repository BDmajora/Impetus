package com.bdmajora.impetus.umbra.gl.uniform;

import org.joml.Vector4f;

import java.util.function.Supplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Four component float vector uniform.
public class Vector4Uniform extends CachedUniform<Vector4f> implements ProgramTargetedUniform {
    // Creates a vector4 uniform from its location and value.
    public Vector4Uniform(int location, Supplier<Vector4f> value) {
        super(location, value, new Vector4f());
    }

    // Copies a new value into the cache.
    @Override
    protected void store(Vector4f cached, Vector4f newValue) {
        cached.set(newValue);
    }

    // Uploads the vector.
    @Override
    protected void upload(Vector4f v) {
        LWJGL.glUniform4f(this.location, v.x, v.y, v.z, v.w);
    }

    // Uploads the vector into the program when it changed.
    @Override
    public void updateIn(int program) {
        this.updateInProgram(program);
    }

    // Uploads the vector into a program that need not be bound.
    @Override
    protected void uploadIn(int program, Vector4f v) {
        LWJGL.glProgramUniform4f(program, this.location, v.x, v.y, v.z, v.w);
    }
}
