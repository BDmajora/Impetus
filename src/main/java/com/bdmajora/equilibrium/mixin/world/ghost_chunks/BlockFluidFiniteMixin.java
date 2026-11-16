package com.bdmajora.equilibrium.mixin.world.ghost_chunks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.BlockFluidFinite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

// Mixin that changes BlockFluidFinite.
@Mixin(BlockFluidFinite.class)
public abstract class BlockFluidFiniteMixin {
    // Runs at the start of updateTick and can cancel it.
    @Inject(method = "updateTick(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)V", at = @At("HEAD"), cancellable = true)
    private void equilibrium$requireLoadedArea(World world, BlockPos pos, IBlockState state, Random rand, CallbackInfo ci) {
        if (!world.isAreaLoaded(pos, 1)) {
            ci.cancel();
        }
    }
}
