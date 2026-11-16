package com.bdmajora.extras.async;

// World that can tick in parallel.
public interface ParallelWorld {
    // Returns whether the world ticks in parallel.
    boolean impetus$isParallel();
}
