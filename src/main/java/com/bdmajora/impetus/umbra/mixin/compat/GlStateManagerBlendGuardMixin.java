package com.bdmajora.impetus.umbra.mixin.compat;

import com.bdmajora.impetus.umbra.gl.blending.BlendOverrideGuard;
import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes GlStateManager.
@Mixin(GlStateManager.class)
public abstract class GlStateManagerBlendGuardMixin {
    // Runs at the start of enableBlend and can cancel it.
    @Inject(method = "enableBlend()V", at = @At("HEAD"), cancellable = true)
    private static void impetus$deferEnableBlend(CallbackInfo ci) {
        if (BlendOverrideGuard.recordEnableBlend()) {
            ci.cancel();
        }
    }
}
