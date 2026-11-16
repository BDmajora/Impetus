package com.bdmajora.equilibrium.mixin.advancements.inventory_trigger;

import com.bdmajora.equilibrium.common.advancements.SlotChangeContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes EntityPlayerMP.
@Mixin(EntityPlayerMP.class)
public abstract class EntityPlayerMPMixin {
    // Wraps the call of InventoryChangeTrigger.trigger in sendSlotContents.
    @WrapOperation(method = "sendSlotContents", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/critereon/InventoryChangeTrigger;trigger(Lnet/minecraft/entity/player/EntityPlayerMP;Lnet/minecraft/entity/player/InventoryPlayer;)V"))
    private void equilibrium$triggerWithContext(InventoryChangeTrigger trigger, EntityPlayerMP player, InventoryPlayer inventory, Operation<Void> original,
                                                 // Container and slot and stack of the change.
                                                 Container container, int slot, ItemStack stack) {
        SlotChangeContext context = (SlotChangeContext) inventory;
        context.equilibrium$setChangedStack(stack);
        try {
            original.call(trigger, player, inventory);
        } finally {
            context.equilibrium$clear();
        }
    }
}
