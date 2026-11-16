package com.bdmajora.extras.mixin.atlas;

import com.bdmajora.extras.client.DriverLimits;
import net.minecraftforge.fml.client.SplashProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes SplashProgress.
@Mixin(value = SplashProgress.class, remap = false)
public abstract class SplashProgressTextureSizeMixin {
    // Runs at the start of getMaxTextureSize and can cancel it.
    @Inject(method = "getMaxTextureSize", at = @At("HEAD"), cancellable = true)
    private static void impetus$queryDriverLimit(CallbackInfoReturnable<Integer> cir) {
        int size = DriverLimits.maxTextureSize();
        if (size > 0) {
            cir.setReturnValue(size);
        }
    }
}
