package com.bdmajora.coarctatio.mixin.state;

import net.minecraft.block.properties.PropertyEnum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// PropertyEnum.hashCode folds in the hash of its whole allowed-value set and its name map on every call, and every property-keyed map lookup (getValue, withProperty, the model selectors) calls it (StellarCore's propertyEnumHashCodeCache); the property is immutable, so the first answer is kept
@Mixin(PropertyEnum.class)
public abstract class PropertyEnumHashMixin {
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
