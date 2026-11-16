package com.bdmajora.impetus.impl.gui;

import com.bdmajora.impetus.engine.api.util.ColorARGB;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

// Blurred copy of the frame drawn behind screens.
public final class ScreenBlurBackdrop {
    // Blur radius in pixels.
    private static final float RADIUS = 3.0f;
    // Number of sample rings.
    private static final int RINGS = 2;
    // Samples per ring.
    private static final int TAPS_PER_RING = 8;
    // Tint color at the top.
    private static final int TINT_TOP = 0xC8101010;
    // Tint color at the bottom.
    private static final int TINT_BOTTOM = 0xDC101010;

    // Texture name of the copy or minus one.
    private static int texture = -1;
    // Width of the copy.
    private static int textureWidth;
    // Height of the copy.
    private static int textureHeight;

    // Prevents instantiation
    private ScreenBlurBackdrop() {
    }

    // Draws the blurred frame over the screen.
    public static void draw(int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        capture(mc.displayWidth, mc.displayHeight);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(texture);
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

    // Copies the frame into the texture.
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

    // Draws the copy with an offset and alpha.
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

    // Draws the tint gradient.
    private static void drawTint(int width, int height) {
        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        tintVertex(buffer, width, 0.0, TINT_TOP);
        tintVertex(buffer, 0.0, 0.0, TINT_TOP);
        tintVertex(buffer, 0.0, height, TINT_BOTTOM);
        tintVertex(buffer, width, height, TINT_BOTTOM);
        tessellator.draw();

        GlStateManager.shadeModel(GL11.GL_FLAT);
    }

    // Adds a tinted vertex.
    private static void tintVertex(BufferBuilder buffer, double x, double y, int argb) {
        buffer.pos(x, y, 0.0)
                .color(ColorARGB.unpackRed(argb), ColorARGB.unpackGreen(argb), ColorARGB.unpackBlue(argb), ColorARGB.unpackAlpha(argb))
                .endVertex();
    }
}
