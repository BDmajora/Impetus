package com.bdmajora.impetus.mixin.features.options;

import net.minecraftforge.client.GuiIngameForge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.bdmajora.impetus.ImpetusVintage;

// Mixin that changes GuiIngameForge.
@Mixin(GuiIngameForge.class)
public class MixinGuiIngameForge {
    // Replaces the call of Minecraft.isFancyGraphicsEnabled in renderGameOverlay.
    @Redirect(method = "renderGameOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isFancyGraphicsEnabled()Z"))
    private boolean impetus$redirectVignette() {
        return ImpetusVintage.options().quality.enableVignette;
    }
}