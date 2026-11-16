package com.bdmajora.impetus.engine.impl.util.collections;

import org.jetbrains.annotations.Nullable;

// Queue that hands out elements.
public interface ReadQueue<E> {
    // Removes and returns the next element or null when empty.
    @Nullable E dequeue();
}
