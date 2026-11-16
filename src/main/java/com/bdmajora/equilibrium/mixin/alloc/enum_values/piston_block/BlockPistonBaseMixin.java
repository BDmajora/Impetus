package com.bdmajora.equilibrium.mixin.alloc.enum_values.piston_block;

import net.minecraft.block.BlockPistonBase;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Mixin that changes BlockPistonBase.
@Mixin(BlockPistonBase.class)
public class BlockPistonBaseMixin {
    // Replaces the call of EnumFacing.values in shouldBeExtended.
    @Redirect(
            method = "shouldBeExtended",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/EnumFacing;values()[Lnet/minecraft/util/EnumFacing;")
    )
    private EnumFacing[] equilibrium$reuseFacingArray() {
        return EnumFacing.VALUES;
    }
}
