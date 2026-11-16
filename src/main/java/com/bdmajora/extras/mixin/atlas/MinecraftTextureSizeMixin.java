package com.bdmajora.extras.mixin.atlas;

import com.bdmajora.extras.client.DriverLimits;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes Minecraft.
@Mixin(Minecraft.class)
public abstract class MinecraftTextureSizeMixin {
    // Runs at the start of getGLMaximumTextureSize and can cancel it.
    @Inject(method = "getGLMaximumTextureSize", at = @At("HEAD"), cancellable = true)
    private static void impetus$queryDriverLimit(CallbackInfoReturnable<Integer> cir) {
        int size = DriverLimits.maxTextureSize();
        if (size > 0) {
            cir.setReturnValue(size);
        }
    }
}
