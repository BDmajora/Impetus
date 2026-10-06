package com.bdmajora.impetus.umbra.pipeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.RayTraceResult;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Replays the block selection box after the composite chain for packs with no gbuffers_line; drawn in the world pass it hits albedo and composite lighting turns the black line dark red beside a torch, so the world matrices are captured and restored here
public final class DeferredBlockOutline {
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);

    private static boolean pending;
    // Set while the replay calls vanilla's drawSelectionBox, so the capture hook lets it through instead of re-capturing into an infinite loop
    private static boolean replaying;

    private static EntityPlayer player;
    private static RayTraceResult target;
    private static float partialTicks;

    private DeferredBlockOutline() {
    }

    // Read by the capture hook to tell our own replay apart from vanilla's original call
    public static boolean isReplaying() {
        return replaying;
    }

    // Records the pending outline and its exact matrices, from the CANCELLED drawSelectionBox, the only point where the projection and modelview are still the world camera's
    public static void capture(EntityPlayer capturedPlayer, RayTraceResult capturedTarget, float capturedPartialTicks) {
        PROJECTION.clear();
        MODELVIEW.clear();
        GL11.glGetFloatv(GL11.GL_PROJECTION_MATRIX, PROJECTION);
        GL11.glGetFloatv(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        PROJECTION.rewind();
        MODELVIEW.rewind();

        player = capturedPlayer;
        target = capturedTarget;
        partialTicks = capturedPartialTicks;
        pending = true;
    }

    // Drops a captured outline without drawing, for an early-ended frame or a vanished pipeline; otherwise a stale capture replays into the next frame around a block no longer looked at
    public static void discard() {
        pending = false;
        player = null;
        target = null;
    }

    // Draws the captured outline into the finished image after the final pass, with Minecraft's framebuffer and its world depth bound so the far edges stay hidden behind the block like vanilla
    public static void drawIfPending() {
        if (!pending) {
            return;
        }
        EntityPlayer capturedPlayer = player;
        RayTraceResult capturedTarget = target;
        discard();
        if (capturedPlayer == null || capturedTarget == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.renderGlobal == null) {
            return;
        }

        // The final pass leaves ITS framebuffer bound, not the presented one; measured, without this the replay lands zero pixels, so bind Minecraft's own
        if (mc.getFramebuffer() != null) {
            mc.getFramebuffer().bindFramebuffer(false);
        }

        // The final pass leaves its own program bound; the outline is fixed-function geometry.
        LWJGL.glUseProgram(0);

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.multMatrix(PROJECTION);
        PROJECTION.rewind();

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.multMatrix(MODELVIEW);
        MODELVIEW.rewind();

        // finishWorldRendering leaves depth off for the fullscreen passes; the box is depth-tested against the world so far edges stay hidden, and writes no depth itself (drawSelectionBox sets depthMask(false))
        GlStateManager.enableDepth();

        replaying = true;
        try {
            mc.renderGlobal.drawSelectionBox(capturedPlayer, capturedTarget, 0, partialTicks);
        } finally {
            replaying = false;

            GlStateManager.matrixMode(GL11.GL_PROJECTION);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            GlStateManager.popMatrix();

            // Hand the composite chain's state back exactly as finishWorldRendering left it.
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.disableBlend();
        }
    }
}
