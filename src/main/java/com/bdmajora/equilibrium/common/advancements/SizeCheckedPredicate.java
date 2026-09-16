package com.bdmajora.equilibrium.common.advancements;

import net.minecraft.item.ItemStack;

// Implemented on ItemPredicate: matches only when the stack's count crossed into the predicate's range with this change, so a stack that has been over the threshold for an hour does not re-run the (tag-comparing) match on every count change
public interface SizeCheckedPredicate {
    boolean equilibrium$matchesCrossing(ItemStack stack);
}
