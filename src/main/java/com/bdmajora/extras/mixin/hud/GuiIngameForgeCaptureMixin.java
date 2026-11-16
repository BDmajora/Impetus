package com.bdmajora.extras.mixin.hud;

import com.bdmajora.extras.client.hud.HudCache;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes GuiIngameForge.
@Mixin(value = GuiIngameForge.class, remap = false)
public abstract class GuiIngameForgeCaptureMixin {
    // Runs at the start of renderCrosshairs and can cancel it.
    @Inject(method = "renderCrosshairs", at = @At("HEAD"), cancellable = true)
    private void impetus$skipCrosshairWhileCapturing(float partialTicks, CallbackInfo ci) {
        if (HudCache.capturing) {
            ci.cancel();
        }
    }
}
