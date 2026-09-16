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

// Two things about how a container mirrors its slots: the mirror starts out empty, so opening any GUI reports every occupied player slot as "changed" and fires the trigger for each; and the mirror is a copy, so the count a slot had before a change is available exactly when the new copy replaces it
@Mixin(Container.class)
public abstract class ContainerMixin {
    // Seed the mirror with a copy of what the player slot already holds; a copy, not the live stack, or in-place count changes would compare equal and never reach the client
    @ModifyArg(method = "addSlotToContainer", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/NonNullList;add(Ljava/lang/Object;)Z", remap = false))
    private Object equilibrium$seedKnownContents(Object empty, @Local(argsOnly = true) Slot slot) {
        if (slot.inventory instanceof InventoryPlayer && slot.getHasStack()) {
            return slot.getStack().copy();
        }
        return empty;
    }

    @Inject(method = "detectAndSendChanges", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isEmpty()Z"))
    private void equilibrium$capturePrevious(CallbackInfo ci, @Local(ordinal = 1) ItemStack previous, @Share("previous") LocalRef<ItemStack> ref) {
        ref.set(previous);
    }

    // Only when the same item is still there; a different item's old count is not a "previous size" of this stack
    @Inject(method = "detectAndSendChanges", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/NonNullList;set(ILjava/lang/Object;)Ljava/lang/Object;", shift = At.Shift.AFTER))
    private void equilibrium$recordPrevious(CallbackInfo ci, @Local(ordinal = 1) ItemStack replacement, @Share("previous") LocalRef<ItemStack> ref) {
        ItemStack previous = ref.get();
        if (previous != null && ItemStack.areItemsEqual(previous, replacement)) {
            ((PreviousStackSize) (Object) replacement).equilibrium$setPreviousCount(previous.getCount());
        }
    }
}
