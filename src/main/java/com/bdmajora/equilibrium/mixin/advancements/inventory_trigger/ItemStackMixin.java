package com.bdmajora.equilibrium.mixin.advancements.inventory_trigger;

import com.bdmajora.equilibrium.common.advancements.PreviousStackSize;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

// Mixin that changes ItemStack.
@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements PreviousStackSize {
    // Count before the change.
    private int equilibrium$previousCount;

    // Handles previous count.
    @Override
    public int equilibrium$previousCount() {
        return this.equilibrium$previousCount;
    }

    // Handles set previous count.
    @Override
    public void equilibrium$setPreviousCount(int count) {
        this.equilibrium$previousCount = count;
    }
}
