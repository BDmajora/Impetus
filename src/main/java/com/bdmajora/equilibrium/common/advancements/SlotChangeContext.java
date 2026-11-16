package com.bdmajora.equilibrium.common.advancements;

import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

// Data of the slot change that is being evaluated.
public interface SlotChangeContext {
    // Returns the stack that changed or null.
    @Nullable
    ItemStack equilibrium$changedStack();

    // Stores the stack that changed.
    void equilibrium$setChangedStack(ItemStack stack);

    // Returns the slot tallies or null.
    @Nullable
    SlotCounts equilibrium$slotCounts();

    // Stores the slot tallies.
    void equilibrium$setSlotCounts(int full, int empty, int occupied);

    // Forgets the stored data.
    void equilibrium$clear();

    // Tally of full and empty and occupied slots.
    final class SlotCounts {
        // Number of full slots.
        public final int full;
        // Number of empty slots.
        public final int empty;
        // Number of occupied slots.
        public final int occupied;

        // Creates a tally.
        public SlotCounts(int full, int empty, int occupied) {
            this.full = full;
            this.empty = empty;
            this.occupied = occupied;
        }

        // Returns whether every count is inside its bounds.
        public boolean matches(MinMaxBounds fullBounds, MinMaxBounds emptyBounds, MinMaxBounds occupiedBounds) {
            return fullBounds.test(this.full) && emptyBounds.test(this.empty) && occupiedBounds.test(this.occupied);
        }
    }
}
