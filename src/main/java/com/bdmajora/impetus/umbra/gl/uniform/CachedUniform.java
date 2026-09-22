package com.bdmajora.impetus.umbra.gl.uniform;

import java.util.function.Supplier;

// The shared shape of every vector uniform: pull the supplier, diff against the last upload, and only then touch GL; the cache is copied in place since the supplier may mutate and return the same object, and initialized forces the first upload even when it equals the zero default
abstract class CachedUniform<T> extends Uniform {
    private final Supplier<T> value;
    // Owned copy of the last uploaded value
    private final T cached;
    private boolean initialized;

    protected CachedUniform(int location, Supplier<T> value, T cached) {
        super(location);
        this.value = value;
        this.cached = cached;
    }

    // Uploads only when the value changed
    @Override
    public final void update() {
        T newValue = this.value.get();

        if (!this.initialized || !this.cached.equals(newValue)) {
            this.initialized = true;
            this.store(this.cached, newValue);
            this.upload(newValue);
        }
    }

    // Copies the new value into the owned cache
    protected abstract void store(T cached, T newValue);

    // The glUniform* call for this vector type
    protected abstract void upload(T newValue);
}
