package com.bdmajora.equilibrium.common.world;

// World that can remove unloaded entities.
public interface UnloadedEntityRemover {
    // Removes the entities of unloaded chunks.
    void equilibrium$removeUnloaded();
}
