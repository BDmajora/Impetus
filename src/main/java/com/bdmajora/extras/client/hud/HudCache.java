package com.bdmajora.extras.client.hud;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.mixin.hud.GuiIngameForgeInvoker;
import com.bdmajora.extras.mixin.hud.GuiIngameInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.client.GuiIngameForge;
import org.lwjgl.opengl.GL11;

// Draws the overlay into a buffer that updates less often.
public final class HudCache {
    // Buffer that holds the overlay.
    private static Framebuffer framebuffer;
    // Time of the last capture.
    private static long lastCaptureNanos;
    // Whether the overlay is being drawn into the buffer.
    public static boolean capturing;

    // Prevents instantiation
    private HudCache() {
    }

    // Draws the overlay from the buffer and refreshes it when due.
    public static void render(EntityRenderer renderer, GuiIngame gui, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        ExtrasConfig.HudSettings settings = Extras.options().hud;
        if (!settings.cacheEnabled || !OpenGlHelper.isFramebufferEnabled() || mc.player == null) {
            release();
            gui.renderGameOverlay(partialTicks);
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        long now = System.nanoTime();
        long interval = 1_000_000_000L / Math.max(1, settings.cacheFps);
        boolean capture = framebuffer == null || now - lastCaptureNanos >= interval
                || framebuffer.framebufferWidth != mc.displayWidth || framebuffer.framebufferHeight != mc.displayHeight;
        if (capture) {
            lastCaptureNanos = now;
            capture(gui, partialTicks, mc);
            renderer.setupOverlayRendering();
        }
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
        if (GuiIngameForge.renderVignette && Minecraft.isFancyGraphicsEnabled()) {
            ((GuiIngameInvoker) gui).impetus$renderVignette(mc.player.getBrightness(), resolution);
        }
        if (gui instanceof GuiIngameForge) {
            ((GuiIngameForgeInvoker) gui).impetus$renderCrosshairs(partialTicks);
        }
        composite(resolution);
        GlStateManager.enableDepth();
    }

    // Draws the overlay into the buffer.
    private static void capture(GuiIngame gui, float partialTicks, Minecraft mc) {
        if (framebuffer == null) {
            framebuffer = new Framebuffer(mc.displayWidth, mc.displayHeight, true);
            framebuffer.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);
            framebuffer.setFramebufferFilter(GL11.GL_NEAREST);
        } else if (framebuffer.framebufferWidth != mc.displayWidth || framebuffer.framebufferHeight != mc.displayHeight) {
            framebuffer.createBindFramebuffer(mc.displayWidth, mc.displayHeight);
            framebuffer.setFramebufferFilter(GL11.GL_NEAREST);
        }
        framebuffer.framebufferClear();
        framebuffer.bindFramebuffer(false);
        // Sets the capturing flag before the blend change.
        capturing = true;
        GlStateManager.disableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        try {
            gui.renderGameOverlay(partialTicks);
        } finally {
            capturing = false;
        }
        mc.getFramebuffer().bindFramebuffer(false);
        GlStateManager.enableBlend();
    }

    // Draws the buffer over the screen.
    private static void composite(ScaledResolution resolution) {
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        framebuffer.bindFramebufferTexture();
        float width = (float) resolution.getScaledWidth_double();
        float height = (float) resolution.getScaledHeight_double();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(0.0D, height, 0.0D).tex(0.0D, 0.0D).endVertex();
        buffer.pos(width, height, 0.0D).tex(1.0D, 0.0D).endVertex();
        buffer.pos(width, 0.0D, 0.0D).tex(1.0D, 1.0D).endVertex();
        buffer.pos(0.0D, 0.0D, 0.0D).tex(0.0D, 1.0D).endVertex();
        tessellator.draw();
        framebuffer.unbindFramebufferTexture();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
    }

    // Binds the buffer when it exists.
    public static void bindCache(boolean viewport) {
        if (framebuffer != null) {
            framebuffer.bindFramebuffer(viewport);
        }
    }

    // Deletes the buffer.
    public static void release() {
        if (framebuffer != null) {
            framebuffer.deleteFramebuffer();
            framebuffer = null;
        }
    }
}
