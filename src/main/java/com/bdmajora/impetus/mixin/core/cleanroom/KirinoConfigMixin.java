package com.bdmajora.impetus.mixin.core.cleanroom;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes KirinoConfig.
@Mixin(targets = "com.cleanroommc.kirino.config.KirinoConfig", remap = false)
public class KirinoConfigMixin {
    // Runs at the start of isEnabled and can cancel it.
    @Inject(method = "isEnabled", at = @At("HEAD"), cancellable = true)
    private static void impetus$disableKirino(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
