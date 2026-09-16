package com.bdmajora.equilibrium.mixin.advancements.inventory_trigger;

import com.bdmajora.equilibrium.common.advancements.PreviousStackSize;
import com.bdmajora.equilibrium.common.advancements.SlotChangeContext;
import com.bdmajora.equilibrium.common.advancements.StackSizeThresholds;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// The gate (Universal Tweaks, after Icterine): a change that emptied a slot, shrank a stack, or grew one without crossing any count an advancement asks for cannot complete an inventory_changed criterion, so nothing is scanned; otherwise the slot tallies are computed once here rather than once per listening advancement. A trigger with no stack context, as fired by mods calling it directly, runs vanilla's full check
@Mixin(InventoryChangeTrigger.class)
public abstract class InventoryChangeTriggerMixin {
    @Inject(method = "trigger", at = @At("HEAD"), cancellable = true)
    private void equilibrium$gateOnChangedStack(EntityPlayerMP player, InventoryPlayer inventory, CallbackInfo ci) {
        SlotChangeContext context = (SlotChangeContext) inventory;
        ItemStack changed = context.equilibrium$changedStack();
        if (changed == null) {
            equilibrium$tallySlots(inventory, context);
            return;
        }
        if (changed.isEmpty()
                || changed.getCount() < ((PreviousStackSize) (Object) changed).equilibrium$previousCount()
                || !StackSizeThresholds.crossesAny(changed)) {
            ci.cancel();
            return;
        }
        equilibrium$tallySlots(inventory, context);
    }

    private static void equilibrium$tallySlots(InventoryPlayer inventory, SlotChangeContext context) {
        int full = 0;
        int empty = 0;
        int occupied = 0;
        for (int slot = 0, size = inventory.getSizeInventory(); slot < size; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                empty++;
            } else {
                occupied++;
                if (stack.getCount() >= stack.getMaxStackSize()) {
                    full++;
                }
            }
        }
        context.equilibrium$setSlotCounts(full, empty, occupied);
    }

    // Every count bound an advancement declares becomes a threshold the gate above knows about
    @Inject(method = "deserializeInstance", at = @At("RETURN"))
    private void equilibrium$collectThresholds(JsonObject json, JsonDeserializationContext context, CallbackInfoReturnable<InventoryChangeTrigger.Instance> cir, @Local ItemPredicate[] predicates) {
        for (ItemPredicate predicate : predicates) {
            Float min = ((MinMaxBoundsAccessor) ((ItemPredicateAccessor) predicate).equilibrium$count()).equilibrium$min();
            if (min != null && min > 1.0F) {
                StackSizeThresholds.add((int) min.floatValue());
            }
        }
    }
}
