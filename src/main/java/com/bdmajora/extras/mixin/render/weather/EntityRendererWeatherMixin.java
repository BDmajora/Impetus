package com.bdmajora.extras.mixin.render.weather;

import com.bdmajora.extras.Extras;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes EntityRenderer.
@Mixin(EntityRenderer.class)
public class EntityRendererWeatherMixin {
    // Runs at the start of renderRainSnow and can cancel it.
    @Inject(method = "renderRainSnow", at = @At("HEAD"), cancellable = true)
    private void impetus$renderRainSnow(float partialTicks, CallbackInfo ci) {
        if (!Extras.options().detail.rainSnow) {
            ci.cancel();
        }
    }
}
