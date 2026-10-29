package com.bdmajora.impetus.engine.impl.gl.shader.uniform;

// Typed shader uniform bound to one location in a linked program
public abstract class GlUniform<T> {
    // Location of the uniform in its program or minus one for a location that GL ignores
    protected final int index;

    // Stores the location and rejects a location below minus one
    protected GlUniform(int index) {
        if (index < -1) {
            throw new IllegalArgumentException("Uniform location must not be less than -1");
        }

        this.index = index;
    }

    // Uploads the value to the location in the program in use
    public abstract void set(T value);
}
