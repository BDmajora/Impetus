package com.bdmajora.extras.mixin.atlas;

import com.bdmajora.extras.client.DriverLimits;
import net.minecraftforge.fml.client.SplashProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Forge's copy of the same probe, used while the splash screen owns the context. remap = false, Forge class
@Mixin(value = SplashProgress.class, remap = false)
public abstract class SplashProgressTextureSizeMixin {
    @Inject(method = "getMaxTextureSize", at = @At("HEAD"), cancellable = true)
    private static void impetus$queryDriverLimit(CallbackInfoReturnable<Integer> cir) {
        int size = DriverLimits.maxTextureSize();
        if (size > 0) {
            cir.setReturnValue(size);
        }
    }
}
