package com.bdmajora.equilibrium.common.entity;

// Implemented on Entity: drops the cached flags byte so the next getFlag re-reads the data manager
public interface FlagCache {
    void equilibrium$invalidateFlags();
}
