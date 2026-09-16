package com.bdmajora.equilibrium.mixin.advancements.inventory_trigger;

import com.bdmajora.equilibrium.common.advancements.SlotChangeContext;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

import javax.annotation.Nullable;

// The trigger only receives the inventory, so the changed stack rides along on it for the duration of one trigger call
@Mixin(InventoryPlayer.class)
public abstract class InventoryPlayerMixin implements SlotChangeContext {
    @Nullable
    private ItemStack equilibrium$changed;
    @Nullable
    private SlotCounts equilibrium$counts;

    @Override
    @Nullable
    public ItemStack equilibrium$changedStack() {
        return this.equilibrium$changed;
    }

    @Override
    public void equilibrium$setChangedStack(ItemStack stack) {
        this.equilibrium$changed = stack;
    }

    @Override
    @Nullable
    public SlotCounts equilibrium$slotCounts() {
        return this.equilibrium$counts;
    }

    @Override
    public void equilibrium$setSlotCounts(int full, int empty, int occupied) {
        this.equilibrium$counts = new SlotCounts(full, empty, occupied);
    }

    @Override
    public void equilibrium$clear() {
        this.equilibrium$changed = null;
        this.equilibrium$counts = null;
    }
}
