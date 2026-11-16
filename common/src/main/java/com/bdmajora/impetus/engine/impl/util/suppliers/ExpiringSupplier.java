package com.bdmajora.impetus.engine.impl.util.suppliers;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

// Supplier that keeps its value for a time.
public class ExpiringSupplier<T> implements Supplier<@NotNull T> {
    // Source of the value.
    private final Supplier<@NotNull T> supplier;
    // Lifetime of a value in nanoseconds.
    private final long expiryTime;
    // Time the value was made.
    private long lastTime;
    // Cached value.
    private T lastValue;

    // Creates a supplier and rejects a null source or unit.
    public ExpiringSupplier(Supplier<@NotNull T> supplier, long duration, TimeUnit unit) {
        this.supplier = Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(unit, "unit");
        this.expiryTime = TimeUnit.NANOSECONDS.convert(duration, unit);
    }

    // Returns the cached value and replaces it when it expired.
    @Override
    public T get() {
        if (lastValue == null || (System.nanoTime() - lastTime) >= expiryTime) {
            lastValue = supplier.get();
            Objects.requireNonNull(lastValue, "Returned value may not be null");
            lastTime = System.nanoTime();
        }
        return lastValue;
    }
}
