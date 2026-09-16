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

// The HUD drawn into its own framebuffer at a capped rate and composited every frame (the idea behind Exordium, Gnetum and Patcher's HUD caching): the overlay is hundreds of small immediate-mode draws (hotbar, hearts, chat, F3, every mod's overlay) that cost the same at 300 fps as at 30, and nothing on it changes faster than a tick. The crosshair and vignette are drawn live since one blends against the scene and the other multiplies it; everything else comes from the cache, which is captured with premultiplied alpha so translucent HUD pixels composite the same way they would have drawn directly
public final class HudCache {
    private static Framebuffer framebuffer;
    private static long lastCaptureNanos;
    // Read by the GlStateManager and Framebuffer hooks: true only while the overlay is being drawn into the cache
    public static boolean capturing;

    private HudCache() {
    }

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
        // Live parts first so they sit under the cached layer exactly as they would in one pass: the vignette darkens the scene, the crosshair inverts it
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

    // Draws the overlay into the cache with alpha accumulating as coverage; the framebuffer is cleared to transparent black so untouched pixels contribute nothing when composited
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
        GlStateManager.disableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        capturing = true;
        try {
            gui.renderGameOverlay(partialTicks);
        } finally {
            capturing = false;
        }
        mc.getFramebuffer().bindFramebuffer(false);
        GlStateManager.enableBlend();
    }

    // Straight alpha in the cache was blended with (SRC_ALPHA, ONE_MINUS_SRC_ALPHA) into transparent black, so its colour is already multiplied by coverage: composite with (ONE, ONE_MINUS_SRC_ALPHA)
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

    // For FramebufferCaptureMixin: a bind of the main framebuffer during capture lands here
    public static void bindCache(boolean viewport) {
        if (framebuffer != null) {
            framebuffer.bindFramebuffer(viewport);
        }
    }

    // Frees the GPU memory when the feature is switched off or the player leaves the world
    public static void release() {
        if (framebuffer != null) {
            framebuffer.deleteFramebuffer();
            framebuffer = null;
        }
    }
}
