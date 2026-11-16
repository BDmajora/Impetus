package com.bdmajora.impetus.engine.impl.util.collections;

import org.jetbrains.annotations.NotNull;

// Queue that accepts elements.
public interface WriteQueue<E> {
    // Reserves room for more elements.
    void ensureCapacity(int numElements);

    // Adds an element.
    void enqueue(@NotNull E e);
}
