package com.bdmajora.impetus.umbra.gl.uniform;

// Base of a program uniform.
public abstract class Uniform {
    // Location in the program.
    protected final int location;

    // Creates a uniform from its location.
    protected Uniform(int location) {
        this.location = location;
    }

    // Returns the location.
    public final int getLocation() {
        return this.location;
    }

    // Uploads the current value.
    public abstract void update();
}
