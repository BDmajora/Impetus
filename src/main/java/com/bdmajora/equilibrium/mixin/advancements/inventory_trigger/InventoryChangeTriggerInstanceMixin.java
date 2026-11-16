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

// Mixin that changes Instance.
@Mixin(InventoryChangeTrigger.Instance.class)
public abstract class InventoryChangeTriggerInstanceMixin {
    // Reference to the field full of the target.
    @Shadow
    @Final
    private MinMaxBounds full;

    // Reference to the field empty of the target.
    @Shadow
    @Final
    private MinMaxBounds empty;

    // Reference to the field occupied of the target.
    @Shadow
    @Final
    private MinMaxBounds occupied;

    // Reference to the field items of the target.
    @Shadow
    @Final
    private ItemPredicate[] items;

    // Runs at the start of test and can cancel it.
    @Inject(method = "test", at = @At("HEAD"), cancellable = true)
    private void equilibrium$fastTest(InventoryPlayer inventory, CallbackInfoReturnable<Boolean> cir) {
        SlotChangeContext context = (SlotChangeContext) inventory;
        SlotChangeContext.SlotCounts counts = context.equilibrium$slotCounts();
        if (counts == null) {
            return;
        }
        if (!counts.matches(this.full, this.empty, this.occupied)) {
            cir.setReturnValue(false);
            return;
        }
        ItemStack changed = context.equilibrium$changedStack();
        if (changed == null) {
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
