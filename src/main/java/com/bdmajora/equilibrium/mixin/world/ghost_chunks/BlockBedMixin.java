package com.bdmajora.equilibrium.mixin.world.ghost_chunks;

import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A bed straddling a chunk border asks for its other half on every neighbour update, and World.getBlockState loads the chunk to answer (FoamFix's ghostbuster, beds); if that chunk is not loaded the half cannot have changed, so the update is dropped instead of ghost-loading it
@Mixin(BlockBed.class)
public abstract class BlockBedMixin {
    @Inject(method = "neighborChanged", at = @At("HEAD"), cancellable = true)
    private void equilibrium$skipUnloadedOtherHalf(IBlockState state, World world, BlockPos pos, net.minecraft.block.Block block, BlockPos fromPos, CallbackInfo ci) {
        EnumFacing facing = state.getValue(BlockBed.FACING);
        BlockPos other = state.getValue(BlockBed.PART) == BlockBed.EnumPartType.FOOT ? pos.offset(facing) : pos.offset(facing.getOpposite());
        if (!world.isBlockLoaded(other)) {
            ci.cancel();
        }
    }
}
