package com.bdmajora.equilibrium.common.advancements;

import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import net.minecraft.item.ItemStack;

// Every count an inventory_changed criterion anywhere asks for (Icterine's idea, via Universal Tweaks); a stack that grew without crossing one of these cannot have completed any advancement, so the trigger is skipped outright. 1 is always present so a stack appearing from nothing still triggers
public final class StackSizeThresholds {
    private static final IntSortedSet THRESHOLDS = new IntAVLTreeSet();

    static {
        THRESHOLDS.add(1);
    }

    private StackSizeThresholds() {
    }

    public static synchronized void clear() {
        THRESHOLDS.clear();
        THRESHOLDS.add(1);
    }

    public static synchronized void add(int threshold) {
        THRESHOLDS.add(threshold);
    }

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
