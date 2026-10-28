package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.pipeline.UmbraShadowRenderer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Name plates stay out of the shadow map: the shadow pass replays entity rendering under the player's view angles, so a label there turns with the camera and its shadow and tinted light shaft sweep across the scene whenever the view moves
@Mixin(EntityRenderer.class)
public class NameplateShadowPassMixin {
    @Inject(method = "drawNameplate", at = @At("HEAD"), cancellable = true)
    private static void impetus$noNameplateInShadowPass(FontRenderer font, String text, float x, float y, float z, int verticalShift,
                                                        float viewerYaw, float viewerPitch, boolean thirdPersonFrontal, boolean sneaking,
                                                        CallbackInfo ci) {
        if (Umbra.getRenderingPipeline() != null && UmbraShadowRenderer.isShadowPass()) {
            ci.cancel();
        }
    }
}
