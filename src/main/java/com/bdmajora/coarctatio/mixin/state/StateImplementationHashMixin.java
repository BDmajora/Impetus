package com.bdmajora.coarctatio.mixin.state;

import net.minecraft.block.state.BlockStateContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// A block state's hash is its property map's hash, recomputed from every entry on each call; states are HashMap keys all over modded code (render caches, machine recipes, world-gen replacement tables) and the map never changes after construction (StellarCore's blockStateImplementationHashCodeCache). Coarctatio's own state class inherits this
@Mixin(BlockStateContainer.StateImplementation.class)
public abstract class StateImplementationHashMixin {
    private int coarctatio$hash;
    private boolean coarctatio$hashed;

    @Inject(method = "hashCode", at = @At("HEAD"), cancellable = true)
    private void coarctatio$cachedHash(CallbackInfoReturnable<Integer> cir) {
        if (this.coarctatio$hashed) {
            cir.setReturnValue(this.coarctatio$hash);
        }
    }

    @Inject(method = "hashCode", at = @At("RETURN"))
    private void coarctatio$rememberHash(CallbackInfoReturnable<Integer> cir) {
        this.coarctatio$hash = cir.getReturnValue();
        this.coarctatio$hashed = true;
    }
}
