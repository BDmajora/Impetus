package com.bdmajora.extras.mixin.hud;

import com.bdmajora.extras.client.hud.HudCache;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The crosshair inverts whatever is behind it and carries the attack indicator, so it is drawn live too
@Mixin(value = GuiIngameForge.class, remap = false)
public abstract class GuiIngameForgeCaptureMixin {
    @Inject(method = "renderCrosshairs", at = @At("HEAD"), cancellable = true)
    private void impetus$skipCrosshairWhileCapturing(float partialTicks, CallbackInfo ci) {
        if (HudCache.capturing) {
            ci.cancel();
        }
    }
}
