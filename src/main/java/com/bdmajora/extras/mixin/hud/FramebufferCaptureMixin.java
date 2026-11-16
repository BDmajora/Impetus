package com.bdmajora.extras.mixin.hud;

import com.bdmajora.extras.client.hud.HudCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes Framebuffer.
@Mixin(Framebuffer.class)
public abstract class FramebufferCaptureMixin {
    // Runs at the start of bindFramebuffer and can cancel it.
    @Inject(method = "bindFramebuffer", at = @At("HEAD"), cancellable = true)
    private void impetus$redirectMainBind(boolean viewport, CallbackInfo ci) {
        if (HudCache.capturing && (Object) this == Minecraft.getMinecraft().getFramebuffer()) {
            HudCache.bindCache(viewport);
            ci.cancel();
        }
    }
}
