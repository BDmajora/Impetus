package com.bdmajora.equilibrium.common.advancements;

import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

// Implemented on InventoryPlayer: the stack whose change fired the inventory trigger and the slot tallies computed once per trigger instead of once per listening advancement
public interface SlotChangeContext {
    @Nullable
    ItemStack equilibrium$changedStack();

    void equilibrium$setChangedStack(ItemStack stack);

    // Null when the trigger did not prepare them, i.e. a mod called Instance.test directly
    @Nullable
    SlotCounts equilibrium$slotCounts();

    void equilibrium$setSlotCounts(int full, int empty, int occupied);

    void equilibrium$clear();

    // The three counts vanilla's Instance.test recomputes per advancement by walking the whole inventory
    final class SlotCounts {
        public final int full;
        public final int empty;
        public final int occupied;

        public SlotCounts(int full, int empty, int occupied) {
            this.full = full;
            this.empty = empty;
            this.occupied = occupied;
        }

        public boolean matches(MinMaxBounds fullBounds, MinMaxBounds emptyBounds, MinMaxBounds occupiedBounds) {
            return fullBounds.test(this.full) && emptyBounds.test(this.empty) && occupiedBounds.test(this.occupied);
        }
    }
}
