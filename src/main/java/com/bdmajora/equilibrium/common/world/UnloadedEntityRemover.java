package com.bdmajora.equilibrium.common.world;

// Implemented on World by the entity_cleanup mixin; runs the removal half of updateEntities without the ticking half
public interface UnloadedEntityRemover {
    void equilibrium$removeUnloaded();
}
