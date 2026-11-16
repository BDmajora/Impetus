package com.bdmajora.impetus.umbra.gl.uniform;

import java.util.function.IntSupplier;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Int uniform that uploads on change.
public class IntUniform extends Uniform implements ProgramTargetedUniform {
    // Supplier of the value.
    private final IntSupplier value;
    // Last uploaded value.
    private int cachedValue;
    // Flag set after the first upload.
    private boolean initialized;

    // Creates an int uniform from its location and value.
    public IntUniform(int location, IntSupplier value) {
        super(location);
        this.value = value;
    }

    // Uploads the value when it changed.
    @Override
    public void update() {
        int newValue = this.value.getAsInt();
        if (this.take(newValue)) {
            LWJGL.glUniform1i(this.location, newValue);
        }
    }

    // Uploads the value into the program when it changed, whatever is bound.
    @Override
    public void updateIn(int program) {
        int newValue = this.value.getAsInt();
        if (this.take(newValue)) {
            LWJGL.glProgramUniform1i(program, this.location, newValue);
        }
    }

    // Records the value and returns whether it differs from the last upload.
    private boolean take(int newValue) {
        if (this.initialized && newValue == this.cachedValue) {
            return false;
        }
        this.initialized = true;
        this.cachedValue = newValue;
        return true;
    }
}
