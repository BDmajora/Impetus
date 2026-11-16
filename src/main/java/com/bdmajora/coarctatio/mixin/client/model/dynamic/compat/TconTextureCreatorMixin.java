package com.bdmajora.coarctatio.mixin.client.model.dynamic.compat;

import com.bdmajora.coarctatio.client.model.dynamic.compat.TconTextureExistence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes CustomTextureCreator.
@Pseudo
@Mixin(targets = "slimeknights/tconstruct/library/client/CustomTextureCreator", remap = false)
public abstract class TconTextureCreatorMixin {
    // Runs at the start of exists and can cancel it.
    @Inject(method = "exists", at = @At("HEAD"), cancellable = true)
    private static void coarctatio$answerFromCache(String key, CallbackInfoReturnable<Boolean> cir) {
        Boolean known = TconTextureExistence.known(key);
        if (known != null) {
            cir.setReturnValue(known);
        }
    }

    // Runs before every return of exists.
    @Inject(method = "exists", at = @At("RETURN"))
    private static void coarctatio$remember(String key, CallbackInfoReturnable<Boolean> cir) {
        TconTextureExistence.remember(key, cir.getReturnValueZ());
    }
}
