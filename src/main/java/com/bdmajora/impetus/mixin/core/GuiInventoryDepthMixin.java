package com.bdmajora.impetus.mixin.core;

import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The one draw behind the survival, creative and horse inventories' model; entities render with culling off, so only the depth test hides each box's rear face, and a frame that hands the GUI depth off, read-only, non-LEQUAL or out of step with GlStateManager's cache shows every last-drawn back face and the model faces away
@Mixin(GuiInventory.class)
public abstract class GuiInventoryDepthMixin {
    @Inject(method = "drawEntityOnScreen", at = @At("HEAD"))
    private static void impetus$restoreDepthState(int posX, int posY, int scale, float mouseX, float mouseY,
                                                  EntityLivingBase entity, CallbackInfo ci) {
        // The first call of each pair moves the cache off vanilla's value, so the second always reaches GL
        GlStateManager.disableDepth();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(GL11.GL_ALWAYS);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        // Uncached; Umbra's far-plane sky range would put every fragment at 1.0, where LEQUAL lets each one through
        LWJGL.glDepthRange(0.0, 1.0);
    }
}
