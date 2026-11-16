package com.bdmajora.impetus.impl.compat.littletiles;

// Contract of the baked light tracker.
public interface BakedLightTracker {
    // Stores a light fingerprint and returns whether it changed.
    boolean impetus$lightChanged(int fingerprint);
}
