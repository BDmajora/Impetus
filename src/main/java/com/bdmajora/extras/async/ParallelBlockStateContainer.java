package com.bdmajora.extras.async;

// Implemented on BlockStateContainer by the async mixin: a section palette starts lock-free, and only the chunk hooks of a world that ticks in parallel switch its lock on, since the container itself does not know which world it belongs to
public interface ParallelBlockStateContainer {
    void impetus$enableParallelLock();
}
