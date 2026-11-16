package com.bdmajora.extras.mixin.render.fog;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.client.FogState;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes EntityRenderer.
@Mixin(EntityRenderer.class)
public class EntityRendererFogMixin {
    // Runs before every return of setupFog.
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void impetus$disableFog(int startCoords, float partialTicks, CallbackInfo ci) {
        if (!Extras.options().render.fog && !FogState.isGameplayFog()) {
            GlStateManager.disableFog();
        }
    }
}
