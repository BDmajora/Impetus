package com.bdmajora.equilibrium.mixin.advancements.inventory_trigger;

import com.bdmajora.equilibrium.common.advancements.SlotChangeContext;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

import javax.annotation.Nullable;

// Mixin that changes InventoryPlayer.
@Mixin(InventoryPlayer.class)
public abstract class InventoryPlayerMixin implements SlotChangeContext {
    // Stack that changed.
    @Nullable
    private ItemStack equilibrium$changed;
    // Cached slot counts.
    @Nullable
    private SlotCounts equilibrium$counts;

    // Returns the stack that changed or null for an unknown change.
    @Override
    @Nullable
    public ItemStack equilibrium$changedStack() {
        return this.equilibrium$changed;
    }

    // Records the stack that changed.
    @Override
    public void equilibrium$setChangedStack(ItemStack stack) {
        this.equilibrium$changed = stack;
    }

    // Returns the slot counts taken with the change.
    @Override
    @Nullable
    public SlotCounts equilibrium$slotCounts() {
        return this.equilibrium$counts;
    }

    // Records the slot counts of the inventory.
    @Override
    public void equilibrium$setSlotCounts(int full, int empty, int occupied) {
        this.equilibrium$counts = new SlotCounts(full, empty, occupied);
    }

    // Forgets the recorded change.
    @Override
    public void equilibrium$clear() {
        this.equilibrium$changed = null;
        this.equilibrium$counts = null;
    }
}
