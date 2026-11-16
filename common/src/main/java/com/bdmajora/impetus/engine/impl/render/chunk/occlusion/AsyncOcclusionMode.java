package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

// Which graph searches run on the search thread.
public enum AsyncOcclusionMode {
    // No search runs on the thread.
    NONE,
    // Only the shadow search runs on the thread.
    ONLY_SHADOW,
    // Every search runs on the thread.
    EVERYTHING
}
