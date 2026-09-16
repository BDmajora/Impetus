package com.bdmajora.impetus.impl.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

// A blurred, darkened copy of the world behind an options screen so its text reads against anything; the frame is copied into a texture and redrawn a few times at small offsets with averaging alpha, which is a box blur with no shader, then tinted
public final class ScreenBlurBackdrop {
    // Offsets in GUI pixels, so the softness is the same at every GUI scale
    private static final float RADIUS = 3.0f;
    private static final int RINGS = 2;
    private static final int TAPS_PER_RING = 8;
    // Vanilla's in-world tint is 0xC0101010 to 0xD0101010; a step darker, since the blur already takes the detail out
    private static final int TINT_TOP = 0xC8101010;
    private static final int TINT_BOTTOM = 0xDC101010;

    private static int texture = -1;
    private static int textureWidth;
    private static int textureHeight;

    private ScreenBlurBackdrop() {
    }

    // Called from drawWorldBackground before anything else is drawn, while the framebuffer still holds only the world
    public static void draw(int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        capture(mc.displayWidth, mc.displayHeight);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(texture);
        // Tap n drawn at alpha 1/n over the previous taps averages them equally; the first is the plain copy
        int tap = 1;
        blit(width, height, 0.0f, 0.0f, 1.0f);
        for (int ring = 1; ring <= RINGS; ring++) {
            float radius = RADIUS * ring / RINGS;
            for (int i = 0; i < TAPS_PER_RING; i++) {
                double angle = (i + (ring & 1) * 0.5) * (Math.PI * 2 / TAPS_PER_RING);
                tap++;
                blit(width, height, (float) (Math.cos(angle) * radius), (float) (Math.sin(angle) * radius), 1.0f / tap);
            }
        }
        GlStateManager.disableTexture2D();
        drawTint(width, height);
        GlStateManager.enableTexture2D();
        GlStateManager.enableDepth();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    // Copies the current framebuffer into the texture, reallocating only when the window size changed
    private static void capture(int width, int height) {
        if (texture == -1) {
            texture = GlStateManager.generateTexture();
        }
        GlStateManager.bindTexture(texture);
        if (width != textureWidth || height != textureHeight) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, 0, 0, width, height, 0);
            textureWidth = width;
            textureHeight = height;
        } else {
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, width, height);
        }
    }

    // One screen-sized quad of the captured frame, shifted by (dx, dy); the framebuffer's origin is bottom-left so V runs the other way
    private static void blit(int width, int height, float dx, float dy, float alpha) {
        GlStateManager.color(1.0f, 1.0f, 1.0f, alpha);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(dx, dy + height, 0.0).tex(0.0, 0.0).endVertex();
        buffer.pos(dx + width, dy + height, 0.0).tex(1.0, 0.0).endVertex();
        buffer.pos(dx + width, dy, 0.0).tex(1.0, 1.0).endVertex();
        buffer.pos(dx, dy, 0.0).tex(0.0, 1.0).endVertex();
        tessellator.draw();
    }

    // GuiScreen.drawGradientRect without a screen instance
    private static void drawTint(int width, int height) {
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buffer.pos(width, 0.0, 0.0).color(red(TINT_TOP), green(TINT_TOP), blue(TINT_TOP), alpha(TINT_TOP)).endVertex();
        buffer.pos(0.0, 0.0, 0.0).color(red(TINT_TOP), green(TINT_TOP), blue(TINT_TOP), alpha(TINT_TOP)).endVertex();
        buffer.pos(0.0, height, 0.0).color(red(TINT_BOTTOM), green(TINT_BOTTOM), blue(TINT_BOTTOM), alpha(TINT_BOTTOM)).endVertex();
        buffer.pos(width, height, 0.0).color(red(TINT_BOTTOM), green(TINT_BOTTOM), blue(TINT_BOTTOM), alpha(TINT_BOTTOM)).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(GL11.GL_FLAT);
    }

    private static int alpha(int argb) {
        return argb >>> 24;
    }

    private static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    private static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    private static int blue(int argb) {
        return argb & 0xFF;
    }
}
