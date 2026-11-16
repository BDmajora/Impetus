package com.bdmajora.coarctatio.mixin.state;

import net.minecraft.block.properties.PropertyInteger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes PropertyInteger.
@Mixin(PropertyInteger.class)
public abstract class PropertyIntegerHashMixin {
    // Cached hash.
    private int coarctatio$hash;
    // Flag set once the hash is cached.
    private boolean coarctatio$hashed;

    // Runs at the start of hashCode and can cancel it.
    @Inject(method = "hashCode", at = @At("HEAD"), cancellable = true)
    private void coarctatio$cachedHash(CallbackInfoReturnable<Integer> cir) {
        if (this.coarctatio$hashed) {
            cir.setReturnValue(this.coarctatio$hash);
        }
    }

    // Runs before every return of hashCode.
    @Inject(method = "hashCode", at = @At("RETURN"))
    private void coarctatio$rememberHash(CallbackInfoReturnable<Integer> cir) {
        this.coarctatio$hash = cir.getReturnValue();
        this.coarctatio$hashed = true;
    }
}
