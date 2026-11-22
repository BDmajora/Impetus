package com.bdmajora.extras.mixin.misc;

import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Makes the world-to-screen copy really unblended. Vanilla turns blending off through GlStateManager's cache, which skips the GL
// call when the cache already reads "off"; if any raw glEnable(GL_BLEND) (another mod, a pack) slipped past the cache, the copy blends
// by the world's alpha over the black window, so cleared sky (alpha 0) shows as a black band under the horizon and stars (alpha = star
// brightness) as black dots. The cache already says off after vanilla's call, so a raw disable here keeps the two in step.
@Mixin(Framebuffer.class)
public abstract class FramebufferBlitBlendMixin {
    // Only reached on the disableBlend = true path, since vanilla's call sits inside that branch.
    @Inject(method = "framebufferRenderExt(IIZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;disableBlend()V", shift = At.Shift.AFTER))
    private void impetus$forceBlendOff(int width, int height, boolean disableBlend, CallbackInfo ci) {
        GL11.glDisable(GL11.GL_BLEND);
    }
}
