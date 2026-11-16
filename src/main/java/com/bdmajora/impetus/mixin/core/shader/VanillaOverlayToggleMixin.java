package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.pipeline.VanillaFeatureToggles;
import net.minecraft.client.renderer.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes ItemRenderer.
@Mixin(ItemRenderer.class)
public class VanillaOverlayToggleMixin {
    // Runs at the start of renderWaterOverlayTexture and can cancel it.
    @Inject(method = "renderWaterOverlayTexture", at = @At("HEAD"), cancellable = true, require = 0)
    private void impetus$suppressUnderwaterOverlay(float partialTicks, CallbackInfo ci) {
        if (!VanillaFeatureToggles.shouldRenderUnderwaterOverlay()) {
            ci.cancel();
        }
    }
}
