package com.bdmajora.equilibrium.mixin.advancements.inventory_trigger;

import com.bdmajora.equilibrium.common.advancements.PreviousStackSize;
import com.bdmajora.equilibrium.common.advancements.SizeCheckedPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// Mixin that changes ItemPredicate.
@Mixin(ItemPredicate.class)
public abstract class ItemPredicateMixin implements SizeCheckedPredicate {
    // Reference to the field count of the target.
    @Shadow
    @Final
    private MinMaxBounds count;

    // Reference to the method test of the target.
    @Shadow
    public abstract boolean test(ItemStack stack);

    // Returns whether a count change crossed into the predicate's range.
    @Override
    public boolean equilibrium$matchesCrossing(ItemStack stack) {
        Float min = ((MinMaxBoundsAccessor) this.count).equilibrium$min();
        Float max = ((MinMaxBoundsAccessor) this.count).equilibrium$max();
        int size = stack.getCount();
        int previous = ((PreviousStackSize) (Object) stack).equilibrium$previousCount();
        boolean crossesMin = min == null ? previous == 0 : previous < min && min <= size;
        boolean withinMax = max == null || size <= max;
        return crossesMin && withinMax && this.test(stack);
    }
}
