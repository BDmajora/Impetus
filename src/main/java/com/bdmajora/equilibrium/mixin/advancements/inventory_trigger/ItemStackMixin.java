package com.bdmajora.equilibrium.mixin.advancements.inventory_trigger;

import com.bdmajora.equilibrium.common.advancements.PreviousStackSize;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

// Mixin that changes ItemStack.
@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements PreviousStackSize {
    // Count before the change.
    private int equilibrium$previousCount;

    // Returns the stack's count before the change being reported.
    @Override
    public int equilibrium$previousCount() {
        return this.equilibrium$previousCount;
    }

    // Records the stack's count before a change.
    @Override
    public void equilibrium$setPreviousCount(int count) {
        this.equilibrium$previousCount = count;
    }
}
