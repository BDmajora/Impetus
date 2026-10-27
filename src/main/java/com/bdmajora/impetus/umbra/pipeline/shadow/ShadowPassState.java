package com.bdmajora.impetus.umbra.pipeline.shadow;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Saves the culling and depth state before the shadow pass and puts it back afterwards
public final class ShadowPassState {
    // Face culling switch read by the latest save
    private boolean cullWasEnabled;
    // Depth function in effect when save ran
    private int previousDepthFunc = GL11.GL_LEQUAL;
    // Flag set from a save until its restore
    private boolean saved;

    // Reads the face culling switch and the depth function from GL
    public void save() {
        this.cullWasEnabled = LWJGL.glGetBoolean(GL11.GL_CULL_FACE);
        this.previousDepthFunc = LWJGL.glGetInteger(GL11.GL_DEPTH_FUNC);
        this.saved = true;
    }

    // Enables depth writes with a less or equal test and a far clear depth and disables face culling
    public static void prepare() {
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.clearDepth(1.0D);
        GlStateManager.disableCull();
    }

    // Puts back the saved culling switch and depth function with depth writes on and does nothing without a pending save
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
