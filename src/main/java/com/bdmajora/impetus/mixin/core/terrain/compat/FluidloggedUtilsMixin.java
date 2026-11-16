package com.bdmajora.impetus.mixin.core.terrain.compat;

import com.bdmajora.impetus.impl.world.cloned.ImpetusBlockAccess;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;
import git.jbredwards.fluidlogged_api.api.world.IBlockAccessWrapper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes FluidloggedUtils.
@Pseudo
@Mixin(targets = "git/jbredwards/fluidlogged_api/api/util/FluidloggedUtils", remap = false)
public class FluidloggedUtilsMixin {
    // Runs at the start of getFluidState and can cancel it.
    @Inject(method = "getFluidState(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)Lgit/jbredwards/fluidlogged_api/api/util/FluidState;", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void impetus$sliceFluidState(IBlockAccess world, BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        IBlockAccess slice = impetus$slice(world);
        if (slice != null) {
            cir.setReturnValue(FluidloggedUtils.getFluidState(slice, pos, world.getBlockState(pos)));
        }
    }

    // Runs at the start of getFluidOrReal and can cancel it.
    @Inject(method = "getFluidOrReal(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void impetus$sliceFluidOrReal(IBlockAccess world, BlockPos pos, CallbackInfoReturnable<IBlockState> cir) {
        IBlockAccess slice = impetus$slice(world);
        if (slice != null) {
            cir.setReturnValue(FluidloggedUtils.getFluidOrReal(slice, pos, world.getBlockState(pos)));
        }
    }

    // Handles slice.
    private static IBlockAccess impetus$slice(IBlockAccess world) {
        if (world instanceof ImpetusBlockAccess) {
            return world;
        }
        if (world instanceof IBlockAccessWrapper) {
            IBlockAccess wrapped = ((IBlockAccessWrapper) world).getWrapped();
            if (wrapped instanceof ImpetusBlockAccess) {
                return wrapped;
            }
        }
        return null;
    }
}
