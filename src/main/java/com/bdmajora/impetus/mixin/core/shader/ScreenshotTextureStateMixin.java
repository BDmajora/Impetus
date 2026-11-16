package com.bdmajora.impetus.mixin.core.shader;

import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ScreenShotHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.image.BufferedImage;

// Mixin that changes ScreenShotHelper.
@Mixin(ScreenShotHelper.class)
public class ScreenshotTextureStateMixin {
    // Runs at the start of createScreenshot.
    @Inject(method = "createScreenshot", at = @At("HEAD"))
    private static void impetus$syncTextureUnitBeforeCapture(int width, int height, Framebuffer buffer,
                                                             CallbackInfoReturnable<BufferedImage> cir) {
        GlTextureUnits.resetToUnit0();
    }

    // Runs before every return of createScreenshot.
    @Inject(method = "createScreenshot", at = @At("RETURN"))
    private static void impetus$releaseFramebufferTexture(int width, int height, Framebuffer buffer,
                                                          CallbackInfoReturnable<BufferedImage> cir) {
        GlStateManager.bindTexture(0);
    }
}
