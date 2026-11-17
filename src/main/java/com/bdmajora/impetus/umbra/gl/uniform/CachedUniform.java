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

        if (this.changed(newValue)) {
            this.upload(newValue);
            this.commit(newValue);
        }
    }

    // Uploads into a program that need not be bound.
    protected final void updateInProgram(int program) {
        T newValue = this.value.get();

        if (this.changed(newValue)) {
            this.uploadIn(program, newValue);
            this.commit(newValue);
        }
    }

    // Returns whether the value differs from the last upload.
    private boolean changed(T newValue) {
        return !this.initialized || !this.cached.equals(newValue);
    }

    // Records a value that was uploaded.
    private void commit(T newValue) {
        this.store(this.cached, newValue);
        this.initialized = true;
    }

    // Uploads a value into a program that need not be bound.
    protected void uploadIn(int program, T newValue) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " has no program targeted upload");
    }

    // Copies a new value into the holder.
    protected abstract void store(T cached, T newValue);

    // Uploads a value.
    protected abstract void upload(T newValue);
}
