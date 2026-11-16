package com.bdmajora.equilibrium.mixin.alloc.enum_values.piston_handler;

import net.minecraft.block.state.BlockPistonStructureHelper;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Mixin that changes BlockPistonStructureHelper.
@Mixin(BlockPistonStructureHelper.class)
public class BlockPistonStructureHelperMixin {
    // Replaces the call of EnumFacing.values in addBranchingBlocks.
    @Redirect(
            method = "addBranchingBlocks",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/EnumFacing;values()[Lnet/minecraft/util/EnumFacing;")
    )
    private EnumFacing[] equilibrium$reuseFacingArray() {
        return EnumFacing.VALUES;
    }
}
