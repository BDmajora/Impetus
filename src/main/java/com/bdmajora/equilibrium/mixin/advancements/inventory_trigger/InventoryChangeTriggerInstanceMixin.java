package com.bdmajora.equilibrium.mixin.advancements.inventory_trigger;

import com.bdmajora.equilibrium.common.advancements.SizeCheckedPredicate;
import com.bdmajora.equilibrium.common.advancements.SlotChangeContext;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Vanilla's test walks the whole inventory and matches every predicate against every stack, per advancement, per change; here the precomputed tallies are checked first, then the changed stack must match at least one predicate before any full walk, and a single-predicate criterion (the common shape) never walks at all
@Mixin(InventoryChangeTrigger.Instance.class)
public abstract class InventoryChangeTriggerInstanceMixin {
    @Shadow
    @Final
    private MinMaxBounds full;

    @Shadow
    @Final
    private MinMaxBounds empty;

    @Shadow
    @Final
    private MinMaxBounds occupied;

    @Shadow
    @Final
    private ItemPredicate[] items;

    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void equilibrium$fastTest(InventoryPlayer inventory, CallbackInfoReturnable<Boolean> cir) {
        SlotChangeContext context = (SlotChangeContext) inventory;
        SlotChangeContext.SlotCounts counts = context.equilibrium$slotCounts();
        if (counts == null) {
            // Called outside trigger(), nothing was prepared: vanilla's test
            return;
        }
        if (!counts.matches(this.full, this.empty, this.occupied)) {
            cir.setReturnValue(false);
            return;
        }
        ItemStack changed = context.equilibrium$changedStack();
        if (changed == null) {
            // No stack context: vanilla's scan, but at least the tallies above were shared
            return;
        }
        int count = this.items.length;
        if (count == 0) {
            cir.setReturnValue(true);
            return;
        }
        if (count == 1) {
            cir.setReturnValue(!changed.isEmpty() && ((SizeCheckedPredicate) this.items[0]).equilibrium$matchesCrossing(changed));
            return;
        }
        boolean[] matched = new boolean[count];
        int remaining = count;
        for (int i = 0; i < count; i++) {
            if (((SizeCheckedPredicate) this.items[i]).equilibrium$matchesCrossing(changed)) {
                matched[i] = true;
                remaining--;
                break;
            }
        }
        if (remaining == count) {
            cir.setReturnValue(false);
            return;
        }
        for (int slot = 0, size = inventory.getSizeInventory(); slot < size && remaining > 0; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            for (int i = 0; i < count; i++) {
                if (!matched[i] && ((SizeCheckedPredicate) this.items[i]).equilibrium$matchesCrossing(stack)) {
                    matched[i] = true;
                    if (--remaining == 0) {
                        cir.setReturnValue(true);
                        return;
                    }
                }
            }
        }
        cir.setReturnValue(false);
    }
}
