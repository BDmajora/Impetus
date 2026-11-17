package com.bdmajora.dynamiclights.mixin;

import com.bdmajora.dynamiclights.DynamicLights;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes EntityRenderer.
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    // Publishes the terrain lights at the start of renderWorld before any terrain pass.
    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void impetus$publishTerrainLights(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        DynamicLights.engine().publishTerrainLights(partialTicks);
    }
}
