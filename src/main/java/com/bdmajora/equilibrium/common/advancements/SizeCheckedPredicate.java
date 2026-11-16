package com.bdmajora.equilibrium.common.advancements;

import net.minecraft.item.ItemStack;

// Predicate that can test a count crossing.
public interface SizeCheckedPredicate {
    // Returns whether the stack crossed a count that the predicate tests.
    boolean equilibrium$matchesCrossing(ItemStack stack);
}
