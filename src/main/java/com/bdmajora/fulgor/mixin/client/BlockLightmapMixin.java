package com.bdmajora.fulgor.mixin.client;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes Block.
@SideOnly(Side.CLIENT)
@Mixin(Block.class)
public abstract class BlockLightmapMixin {
    // Runs at the start of getPackedLightmapCoords and can cancel it.
    @Inject(method = "getPackedLightmapCoords", at = @At("HEAD"), cancellable = true)
    private void fulgor$plainLightmapCoords(IBlockState state, IBlockAccess source, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(source.getCombinedLight(pos, state.getLightValue(source, pos)));
    }
}
