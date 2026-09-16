package com.bdmajora.equilibrium.mixin.advancements.inventory_trigger;

import com.bdmajora.equilibrium.common.advancements.PreviousStackSize;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

// One int per stack; only written by ContainerMixin when a slot's count changes in place
@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements PreviousStackSize {
    private int equilibrium$previousCount;

    @Override
    public int equilibrium$previousCount() {
        return this.equilibrium$previousCount;
    }

    @Override
    public void equilibrium$setPreviousCount(int count) {
        this.equilibrium$previousCount = count;
    }
}
