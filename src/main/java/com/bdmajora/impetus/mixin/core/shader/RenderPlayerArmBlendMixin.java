package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Mixin that changes RenderPlayer.
@Mixin(RenderPlayer.class)
public class RenderPlayerArmBlendMixin {
    // Render stage of the solid hand.
    private static final int HAND_SOLID = 16;
    // Render stage of the translucent hand.
    private static final int HAND_TRANSLUCENT = 23;

    // Replaces the call of GlStateManager.enableBlend in renderRightArm and renderLeftArm.
    @Redirect(
            method = {"renderRightArm", "renderLeftArm"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;enableBlend()V"),
            require = 0)
    private void impetus$keepArmUnblended() {
        int renderStage = CapturedRenderingState.INSTANCE.getRenderStage();
        if (renderStage != HAND_SOLID && renderStage != HAND_TRANSLUCENT) {
            GlStateManager.enableBlend();
        }
    }
}
