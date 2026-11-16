package com.bdmajora.equilibrium.mixin.world.block_entity_ticking.sleeping.brewing_stand;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.util.NonNullList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Mixin that changes TileEntityBrewingStand.
@Mixin(TileEntityBrewingStand.class)
public abstract class TileEntityBrewingStandMixin {
    // Reference to the field brewing item stacks of the target.
    @Shadow
    private NonNullList<ItemStack> brewingItemStacks;

    // Reference to the method can brew of the target.
    @Shadow
    private boolean canBrew() {
        throw new AssertionError();
    }

    // Replaces the call of TileEntityBrewingStand.canBrew in update.
    @Redirect(
            method = "update",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/tileentity/TileEntityBrewingStand;canBrew()Z")
    )
    private boolean equilibrium$skipRecipeScanWhenEmpty(TileEntityBrewingStand stand) {
        if (this.brewingItemStacks.get(3).isEmpty()) {
            return false;
        }

        return this.canBrew();
    }
}
