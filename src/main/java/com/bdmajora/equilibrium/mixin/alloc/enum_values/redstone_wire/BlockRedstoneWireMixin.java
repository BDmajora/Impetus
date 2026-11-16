package com.bdmajora.equilibrium.mixin.alloc.enum_values.redstone_wire;

import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Mixin that changes BlockRedstoneWire.
@Mixin(BlockRedstoneWire.class)
public class BlockRedstoneWireMixin {
    // Replaces the call of EnumFacing.values in notifyWireNeighborsOfStateChange and breakBlock.
    @Redirect(
            method = {"notifyWireNeighborsOfStateChange", "breakBlock"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/EnumFacing;values()[Lnet/minecraft/util/EnumFacing;")
    )
    private EnumFacing[] equilibrium$reuseFacingArray() {
        return EnumFacing.VALUES;
    }
}
