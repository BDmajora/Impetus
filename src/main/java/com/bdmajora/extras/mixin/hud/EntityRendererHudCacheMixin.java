package com.bdmajora.extras.mixin.hud;

import com.bdmajora.extras.client.hud.HudCache;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// The one place the in-game overlay is drawn each frame
@Mixin(EntityRenderer.class)
public abstract class EntityRendererHudCacheMixin {
    @Redirect(method = "updateCameraAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;renderGameOverlay(F)V"))
    private void impetus$renderThroughCache(GuiIngame gui, float partialTicks) {
        HudCache.render((EntityRenderer) (Object) this, gui, partialTicks);
    }
}
