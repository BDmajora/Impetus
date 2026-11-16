package com.bdmajora.equilibrium.mixin.advancements.inventory_trigger;

import com.bdmajora.equilibrium.common.advancements.PreviousStackSize;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes Container.
@Mixin(Container.class)
public abstract class ContainerMixin {
    // Changes an argument of the call of NonNullList.add in addSlotToContainer.
    @ModifyArg(method = "addSlotToContainer", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/NonNullList;add(Ljava/lang/Object;)Z", remap = false))
    private Object equilibrium$seedKnownContents(Object empty, @Local(argsOnly = true) Slot slot) {
        if (slot.inventory instanceof InventoryPlayer && slot.getHasStack()) {
            return slot.getStack().copy();
        }
        return empty;
    }

    // Runs around the call of ItemStack.isEmpty in detectAndSendChanges.
    @Inject(method = "detectAndSendChanges", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isEmpty()Z"))
    private void equilibrium$capturePrevious(CallbackInfo ci, @Local(ordinal = 1) ItemStack previous, @Share("previous") LocalRef<ItemStack> ref) {
        ref.set(previous);
    }

    // Runs around the call of NonNullList.set in detectAndSendChanges.
    @Inject(method = "detectAndSendChanges", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/NonNullList;set(ILjava/lang/Object;)Ljava/lang/Object;", shift = At.Shift.AFTER))
    private void equilibrium$recordPrevious(CallbackInfo ci, @Local(ordinal = 1) ItemStack replacement, @Share("previous") LocalRef<ItemStack> ref) {
        ItemStack previous = ref.get();
        if (previous != null && ItemStack.areItemsEqual(previous, replacement)) {
            ((PreviousStackSize) (Object) replacement).equilibrium$setPreviousCount(previous.getCount());
        }
    }
}
