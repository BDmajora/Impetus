package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.pipeline.VanillaFeatureToggles;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes EntityRenderer.
@Mixin(EntityRenderer.class)
public class VanillaFeatureToggleMixin {
    // Runs at the start of renderRainSnow and can cancel it.
    @Inject(method = "renderRainSnow", at = @At("HEAD"), cancellable = true, require = 0)
    private void impetus$suppressWeather(float partialTicks, CallbackInfo ci) {
        if (!VanillaFeatureToggles.shouldRenderWeather()) {
            ci.cancel();
        }
    }
}
