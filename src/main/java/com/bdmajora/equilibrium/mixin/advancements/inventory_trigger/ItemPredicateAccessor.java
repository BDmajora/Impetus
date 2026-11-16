package com.bdmajora.equilibrium.mixin.advancements.inventory_trigger;

import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Mixin that changes ItemPredicate.
@Mixin(ItemPredicate.class)
public interface ItemPredicateAccessor {
    // Gives access to the field count.
    @Accessor("count")
    MinMaxBounds equilibrium$count();
}
