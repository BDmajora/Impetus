package com.bdmajora.equilibrium.mixin.advancements.inventory_trigger;

import net.minecraft.advancements.critereon.MinMaxBounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable;

@Mixin(MinMaxBounds.class)
public interface MinMaxBoundsAccessor {
    @Accessor("min")
    @Nullable
    Float equilibrium$min();

    @Accessor("max")
    @Nullable
    Float equilibrium$max();
}
