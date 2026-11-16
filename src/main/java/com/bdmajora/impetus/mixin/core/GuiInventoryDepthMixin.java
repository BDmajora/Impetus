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

// Mixin that changes GuiInventory.
@Mixin(GuiInventory.class)
public abstract class GuiInventoryDepthMixin {
    // Runs at the start of drawEntityOnScreen.
    @Inject(method = "drawEntityOnScreen", at = @At("HEAD"))
    private static void impetus$restoreDepthState(int posX, int posY, int scale, float mouseX, float mouseY,
                                                  // Entity drawn in the inventory.
                                                  EntityLivingBase entity, CallbackInfo ci) {
        GlStateManager.disableDepth();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(GL11.GL_ALWAYS);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        LWJGL.glDepthRange(0.0, 1.0);
    }
}
