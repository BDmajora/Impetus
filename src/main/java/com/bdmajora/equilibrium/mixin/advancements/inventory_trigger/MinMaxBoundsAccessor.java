package com.bdmajora.equilibrium.mixin.advancements.inventory_trigger;

import net.minecraft.advancements.critereon.MinMaxBounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable;

// Mixin that changes MinMaxBounds.
@Mixin(MinMaxBounds.class)
public interface MinMaxBoundsAccessor {
    // Gives access to the field min.
    @Accessor("min")
    @Nullable
    Float equilibrium$min();

    // Gives access to the field max.
    @Accessor("max")
    @Nullable
    Float equilibrium$max();
}
