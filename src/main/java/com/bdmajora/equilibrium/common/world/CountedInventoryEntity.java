package com.bdmajora.equilibrium.common.world;

import net.minecraft.world.World;

import javax.annotation.Nullable;

// Entity that remembers the world that counted it.
public interface CountedInventoryEntity {
    // Returns the world that counted the entity or null.
    @Nullable
    World equilibrium$getCountedWorld();

    // Stores the world that counted the entity.
    void equilibrium$setCountedWorld(@Nullable World world);
}
