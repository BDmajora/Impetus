package com.bdmajora.extras.mixin.misc;

import com.bdmajora.extras.Extras;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Mixin that changes GuiIngame.
@Mixin(GuiIngame.class)
public class GuiIngameMixin {
    // Runs at the start of renderSelectedItem and can cancel it.
    @Inject(method = "renderSelectedItem", at = @At("HEAD"), cancellable = true)
    private void impetus$renderSelectedItem(ScaledResolution resolution, CallbackInfo ci) {
        if (!Extras.options().detail.heldItemTooltips) {
            ci.cancel();
        }
    }
}
