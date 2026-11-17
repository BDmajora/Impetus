package com.bdmajora.impetus.umbra.gl.uniform;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Float uniform that uploads on change.
public class FloatUniform extends Uniform implements ProgramTargetedUniform {
    // Supplier of the value.
    private final FloatSupplier value;
    // Last uploaded value.
    private float cachedValue;
    // Flag set after the first upload.
    private boolean initialized;

    // Creates a float uniform from its location and value.
    public FloatUniform(int location, FloatSupplier value) {
        super(location);
        this.value = value;
    }

    // Uploads the value when it changed.
    @Override
    public void update() {
        float newValue = this.value.getAsFloat();
        if (this.take(newValue)) {
            LWJGL.glUniform1f(this.location, newValue);
        }
    }

    // Uploads the value into the program when it changed.
    @Override
    public void updateIn(int program) {
        float newValue = this.value.getAsFloat();
        if (this.take(newValue)) {
            LWJGL.glProgramUniform1f(program, this.location, newValue);
        }
    }

    // Records the value and returns whether it differs from the last upload.
    private boolean take(float newValue) {
        // Skips the upload when the value is unchanged.
        if (this.initialized && newValue == this.cachedValue) {
            return false;
        }
        this.initialized = true;
        this.cachedValue = newValue;
        return true;
    }
}
