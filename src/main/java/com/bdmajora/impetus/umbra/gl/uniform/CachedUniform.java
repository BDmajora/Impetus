package com.bdmajora.impetus.umbra.gl.uniform;

import java.util.function.Supplier;

// Uniform that uploads only when its value changes.
abstract class CachedUniform<T> extends Uniform {
    // Source of the value.
    private final Supplier<T> value;
    // Copy of the last uploaded value.
    private final T cached;
    // Flag set after the first upload.
    private boolean initialized;

    // Creates a uniform with a holder of the last value.
    protected CachedUniform(int location, Supplier<T> value, T cached) {
        super(location);
        this.value = value;
        this.cached = cached;
    }

    // Uploads the value on the first call and whenever it changes and retries after a failed upload.
    @Override
    public final void update() {
        T newValue = this.value.get();

        if (!this.initialized || !this.cached.equals(newValue)) {
            this.upload(newValue);
            this.store(this.cached, newValue);
            this.initialized = true;
        }
    }

    // Copies a new value into the holder.
    protected abstract void store(T cached, T newValue);

    // Uploads a value.
    protected abstract void upload(T newValue);
}
