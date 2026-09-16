package com.bdmajora.extras.mixin.hud;

import com.bdmajora.extras.client.hud.HudCache;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The vignette multiplies the scene and cannot be cached over a transparent background; HudCache draws it live instead
@Mixin(GuiIngame.class)
public abstract class GuiIngameCaptureMixin {
    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void impetus$skipVignetteWhileCapturing(float lightLevel, ScaledResolution resolution, CallbackInfo ci) {
        if (HudCache.capturing) {
            ci.cancel();
        }
    }
}
