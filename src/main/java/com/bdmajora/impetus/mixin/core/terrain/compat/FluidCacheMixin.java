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

// Fluidlogged's FluidCache wraps the block access a fluid is rendered through and, once it can reach chunks (WorldSlice implements IChunkProvider so its tile entity lookups work), reads block and fluid states from the live chunk instead. For the chunk builder that races the main thread and skips the slice's guessed fluid states (FluidloggingInference), so both reads are sent back to the slice, which holds the same snapshot the mesh is built from; tile entities keep going through the chunk. Applied only when Fluidlogged is present, and a FluidCache without these methods is left alone
@Pseudo
@Mixin(targets = "git/jbredwards/fluidlogged_api/mod/common/fluid/util/FluidCache", remap = false)
public abstract class FluidCacheMixin {
    @Inject(method = "getBlockState(III)Lnet/minecraft/block/state/IBlockState;", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void impetus$sliceBlockState(int x, int y, int z, CallbackInfoReturnable<IBlockState> cir) {
        IBlockAccess wrapped = ((IBlockAccessWrapper) (Object) this).getWrapped();
        if (wrapped instanceof ImpetusBlockAccess) {
            cir.setReturnValue(((ImpetusBlockAccess) wrapped).getBlockState(x, y, z));
        }
    }

    @Inject(method = "getFluidState(III)Lgit/jbredwards/fluidlogged_api/api/util/FluidState;", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void impetus$sliceFluidState(int x, int y, int z, CallbackInfoReturnable<FluidState> cir) {
        IBlockAccess wrapped = ((IBlockAccessWrapper) (Object) this).getWrapped();
        if (wrapped instanceof ImpetusBlockAccess) {
            cir.setReturnValue(((ImpetusBlockAccess) wrapped).getFluidState(x, y, z));
        }
    }
}
