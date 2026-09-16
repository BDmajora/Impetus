package com.bdmajora.extras.mixin.hud;

import com.bdmajora.extras.client.hud.HudCache;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Keeps the cache's alpha channel meaning "coverage" no matter what blend an overlay asks for: the colour factors are honoured, the alpha factors are forced to accumulate, and a translucent colour drawn with blending off still lands as an opaque pixel, as it would have on screen. Each hook is one static boolean test when the cache is not capturing
@Mixin(GlStateManager.class)
public abstract class GlStateManagerCaptureMixin {
    private static boolean impetus$blendEnabled;

    @Inject(method = "blendFunc(II)V", at = @At("HEAD"), cancellable = true)
    private static void impetus$captureBlendFunc(int src, int dst, CallbackInfo ci) {
        if (HudCache.capturing) {
            OpenGlHelper.glBlendFunc(src, dst, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            ci.cancel();
        }
    }

    @Inject(method = "tryBlendFuncSeparate(IIII)V", at = @At("HEAD"), cancellable = true)
    private static void impetus$captureBlendFuncSeparate(int src, int dst, int srcAlpha, int dstAlpha, CallbackInfo ci) {
        if (HudCache.capturing && dstAlpha != GL11.GL_ONE_MINUS_SRC_ALPHA) {
            OpenGlHelper.glBlendFunc(src, dst, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            ci.cancel();
        }
    }

    @Inject(method = "enableBlend", at = @At("HEAD"))
    private static void impetus$noteBlendOn(CallbackInfo ci) {
        if (HudCache.capturing) {
            impetus$blendEnabled = true;
        }
    }

    @Inject(method = "disableBlend", at = @At("HEAD"))
    private static void impetus$noteBlendOff(CallbackInfo ci) {
        if (HudCache.capturing) {
            impetus$blendEnabled = false;
        }
    }

    @Inject(method = "color(FFFF)V", at = @At("HEAD"), cancellable = true)
    private static void impetus$opaqueWhenUnblended(float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (HudCache.capturing && !impetus$blendEnabled && alpha < 1.0F) {
            GlStateManager.color(red, green, blue, 1.0F);
            ci.cancel();
        }
    }
}
