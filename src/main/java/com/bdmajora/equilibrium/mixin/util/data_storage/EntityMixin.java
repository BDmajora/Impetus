package com.bdmajora.equilibrium.mixin.util.data_storage;

import com.bdmajora.equilibrium.common.world.CountedInventoryEntity;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nullable;

// Mixin that changes Entity.
@Mixin(Entity.class)
public abstract class EntityMixin implements CountedInventoryEntity {
    // World whose inventory entity count includes this entity.
    @Unique
    private World equilibrium$countedWorld;

    // Returns the counted world.
    @Nullable
    @Override
    public World equilibrium$getCountedWorld() {
        return this.equilibrium$countedWorld;
    }

    // Records which world counts this entity.
    @Override
    public void equilibrium$setCountedWorld(@Nullable World world) {
        this.equilibrium$countedWorld = world;
    }
}
