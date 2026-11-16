package com.bdmajora.equilibrium.mixin.world.block_entity_ticking.sleeping.furnace;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.NonNullList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes TileEntityFurnace.
@Mixin(TileEntityFurnace.class)
public abstract class TileEntityFurnaceMixin {
    // Reference to the field furnace item stacks of the target.
    @Shadow
    private NonNullList<ItemStack> furnaceItemStacks;

    // Reference to the field furnace burn time of the target.
    @Shadow
    private int furnaceBurnTime;

    // Reference to the field cook time of the target.
    @Shadow
    private int cookTime;

    // Runs at the start of update and can cancel it.
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void equilibrium$sleepWhileIdle(CallbackInfo ci) {
        if (this.furnaceBurnTime != 0 || this.cookTime != 0) {
            return;
        }

        if (!this.furnaceItemStacks.get(0).isEmpty() && !this.furnaceItemStacks.get(1).isEmpty()) {
            return;
        }

        ci.cancel();
    }
}
