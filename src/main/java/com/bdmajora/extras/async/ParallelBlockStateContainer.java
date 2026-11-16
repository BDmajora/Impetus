package com.bdmajora.extras.async;

// Block state container that can be locked for parallel use.
public interface ParallelBlockStateContainer {
    // Turns on locking of the container.
    void impetus$enableParallelLock();
}
