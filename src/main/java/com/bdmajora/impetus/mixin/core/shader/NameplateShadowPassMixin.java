package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes EntityRenderer.
@Mixin(EntityRenderer.class)
public class NameplateShadowPassMixin {
    // Runs at the start of drawNameplate and can cancel it.
    @Inject(method = "drawNameplate", at = @At("HEAD"), cancellable = true)
    private static void impetus$noNameplateInShadowPass(FontRenderer font, String text, float x, float y, float z, int verticalShift,
                                                        // Viewer angles and view flags.
                                                        float viewerYaw, float viewerPitch, boolean thirdPersonFrontal, boolean sneaking,
                                                        CallbackInfo ci) {
        if (Umbra.getRenderingPipeline() != null && UmbraShadowRenderer.isShadowPass()) {
            ci.cancel();
        }
    }
}
