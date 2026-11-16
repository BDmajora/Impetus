package com.bdmajora.impetus.umbra.gl.uniform;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Float uniform that uploads on change.
public class FloatUniform extends Uniform {
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
        if (!this.initialized || newValue != this.cachedValue) {
            this.initialized = true;
            this.cachedValue = newValue;
            LWJGL.glUniform1f(this.location, newValue);
        }
    }
}
