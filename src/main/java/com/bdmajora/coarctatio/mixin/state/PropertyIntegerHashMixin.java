package com.bdmajora.coarctatio.mixin.state;

import net.minecraft.block.properties.PropertyInteger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Same for PropertyInteger, whose hash walks its allowed-value set
@Mixin(PropertyInteger.class)
public abstract class PropertyIntegerHashMixin {
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
