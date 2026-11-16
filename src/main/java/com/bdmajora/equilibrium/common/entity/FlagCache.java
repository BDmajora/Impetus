package com.bdmajora.equilibrium.common.entity;

// Entity that caches its flags.
public interface FlagCache {
    // Discards the cached flags.
    void equilibrium$invalidateFlags();
}
