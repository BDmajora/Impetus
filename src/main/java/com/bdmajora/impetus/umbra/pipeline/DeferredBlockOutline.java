package com.bdmajora.impetus.umbra.pipeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.RayTraceResult;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Captures the block selection outline and draws it after the shader pack passes.
public final class DeferredBlockOutline {
    // Projection matrix at capture time.
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    // Model view matrix at capture time.
    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);

    // Pending state of a captured outline.
    private static boolean pending;
    // Replay state of the outline draw.
    private static boolean replaying;

    // Player of the captured outline or null.
    private static EntityPlayer player;
    // Target of the captured outline or null.
    private static RayTraceResult target;
    // Partial ticks of the captured outline.
    private static float partialTicks;

    // Prevents instantiation
    private DeferredBlockOutline() {
    }

    // Returns whether the outline is being drawn.
    public static boolean isReplaying() {
        return replaying;
    }

    // Records the matrices and target of an outline draw.
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

    // Forgets the captured outline.
    public static void discard() {
        pending = false;
        player = null;
        target = null;
    }

    // Draws the captured outline once with the captured matrices and restores the state.
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

        if (mc.getFramebuffer() != null) {
            mc.getFramebuffer().bindFramebuffer(false);
        }

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

            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
        }
    }
}
