package com.bdmajora.equilibrium.common.advancements;

import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import net.minecraft.item.ItemStack;

// Counts at which a stack change can satisfy an advancement.
public final class StackSizeThresholds {
    // Sorted counts that matter.
    private static final IntSortedSet THRESHOLDS = new IntAVLTreeSet();

    static {
        THRESHOLDS.add(1);
    }

    // Prevents instantiation
    private StackSizeThresholds() {
    }

    // Resets the counts to only one.
    public static synchronized void clear() {
        THRESHOLDS.clear();
        THRESHOLDS.add(1);
    }

    // Adds a count that matters.
    public static synchronized void add(int threshold) {
        THRESHOLDS.add(threshold);
    }

    // Returns whether the stack grew across a count that matters.
    public static synchronized boolean crossesAny(ItemStack stack) {
        int previous = ((PreviousStackSize) (Object) stack).equilibrium$previousCount();
        int current = stack.getCount();
        for (IntIterator it = THRESHOLDS.iterator(); it.hasNext(); ) {
            int threshold = it.nextInt();
            if (threshold > current) {
                return false;
            }
            if (previous < threshold) {
                return true;
            }
        }
        return false;
    }
}
