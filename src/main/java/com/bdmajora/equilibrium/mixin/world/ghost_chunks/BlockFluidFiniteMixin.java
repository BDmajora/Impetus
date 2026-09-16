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

// Finite fluids only ever touch their direct neighbours, so a one-block margin is enough (FoamFix's ghostbuster, fluids). remap = false, Forge class
@Mixin(value = BlockFluidFinite.class, remap = false)
public abstract class BlockFluidFiniteMixin {
    @Inject(method = "updateTick", at = @At("HEAD"), cancellable = true)
    private void equilibrium$requireLoadedArea(World world, BlockPos pos, IBlockState state, Random rand, CallbackInfo ci) {
        if (!world.isAreaLoaded(pos, 1)) {
            ci.cancel();
        }
    }
}
