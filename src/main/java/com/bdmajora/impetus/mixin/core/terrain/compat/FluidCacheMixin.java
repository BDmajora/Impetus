package com.bdmajora.impetus.mixin.core.terrain.compat;

import com.bdmajora.impetus.impl.world.cloned.ImpetusBlockAccess;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.world.IBlockAccessWrapper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes FluidCache.
@Pseudo
@Mixin(targets = "git/jbredwards/fluidlogged_api/mod/common/fluid/util/FluidCache", remap = false)
public abstract class FluidCacheMixin {
    // Runs at the start of getBlockState and can cancel it.
    @Inject(method = "getBlockState(III)Lnet/minecraft/block/state/IBlockState;", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void impetus$sliceBlockState(int x, int y, int z, CallbackInfoReturnable<IBlockState> cir) {
        IBlockAccess wrapped = ((IBlockAccessWrapper) (Object) this).getWrapped();
        if (wrapped instanceof ImpetusBlockAccess) {
            cir.setReturnValue(((ImpetusBlockAccess) wrapped).getBlockState(x, y, z));
        }
    }

    // Runs at the start of getFluidState and can cancel it.
    @Inject(method = "getFluidState(III)Lgit/jbredwards/fluidlogged_api/api/util/FluidState;", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void impetus$sliceFluidState(int x, int y, int z, CallbackInfoReturnable<FluidState> cir) {
        IBlockAccess wrapped = ((IBlockAccessWrapper) (Object) this).getWrapped();
        if (wrapped instanceof ImpetusBlockAccess) {
            cir.setReturnValue(((ImpetusBlockAccess) wrapped).getFluidState(x, y, z));
        }
    }
}
