package com.bdmajora.extras.async;

// Implemented on World by the async mixin; true once the concurrent collections were installed at construction, which is the precondition for ticking anything off-thread
public interface ParallelWorld {
    boolean impetus$isParallel();
}
