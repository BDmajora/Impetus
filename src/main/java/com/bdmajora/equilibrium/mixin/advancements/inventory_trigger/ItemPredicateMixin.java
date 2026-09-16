package com.bdmajora.equilibrium.mixin.advancements.inventory_trigger;

import com.bdmajora.equilibrium.common.advancements.PreviousStackSize;
import com.bdmajora.equilibrium.common.advancements.SizeCheckedPredicate;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// The count test is an int compare and the item test can be an NBT/ore-dictionary walk, so the count goes first and is phrased as a crossing: the stack must have entered the range with this change
@Mixin(ItemPredicate.class)
public abstract class ItemPredicateMixin implements SizeCheckedPredicate {
    @Shadow
    @Final
    private MinMaxBounds count;

    @Shadow
    public abstract boolean test(ItemStack stack);

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
