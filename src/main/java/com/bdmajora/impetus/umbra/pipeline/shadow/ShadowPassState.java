package com.bdmajora.impetus.umbra.pipeline.shadow;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Saver of the GL state around the shadow pass.
public final class ShadowPassState {
    // Flag set when culling was on.
    private boolean cullWasEnabled;
    // Depth function before the pass.
    private int previousDepthFunc = GL11.GL_LEQUAL;
    // Flag set while a state is saved.
    private boolean saved;

    // Saves the viewport and bound framebuffer.
    public void save() {
        this.cullWasEnabled = LWJGL.glGetBoolean(GL11.GL_CULL_FACE);
        this.previousDepthFunc = LWJGL.glGetInteger(GL11.GL_DEPTH_FUNC);
        this.saved = true;
    }

    // Sets the GL state for drawing shadows.
    public static void prepare() {
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.clearDepth(1.0D);
        GlStateManager.disableCull();
    }

    // Restores the saved state.
    public void restore() {
        if (!this.saved) {
            return;
        }
        this.saved = false;
        if (this.cullWasEnabled) {
            GlStateManager.enableCull();
        } else {
            GlStateManager.disableCull();
        }
        GlStateManager.depthFunc(this.previousDepthFunc);
        GlStateManager.depthMask(true);
    }
}
