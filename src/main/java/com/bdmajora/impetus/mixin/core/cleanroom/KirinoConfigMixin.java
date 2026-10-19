package com.bdmajora.impetus.mixin.core.cleanroom;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keep Kirino's terrain runtime off; Impetus owns terrain rendering and block/light updates
@Mixin(targets = "com.cleanroommc.kirino.config.KirinoConfig", remap = false)
public class KirinoConfigMixin {
    @Inject(method = "isEnabled", at = @At("HEAD"), cancellable = true)
    private static void impetus$disableKirino(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
