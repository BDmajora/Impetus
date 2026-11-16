package com.bdmajora.impetus.engine.impl.util.position;

// Supplier of a value for a block position.
@FunctionalInterface
public interface PositionalSupplier<T> {
    // Returns the value at a position.
    T getAt(int x, int y, int z);
}
