package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Draws Distant Horizons terrain into the shadow map through the Distant Horizons renderer
public final class DhShadowLods {
    // Prevents instantiation
    private DhShadowLods() {
    }

    // Returns whether the active pack draws Distant Horizons terrain into the shadow map
    public static boolean active() {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline == null) {
            return false;
        }
        DhCompat compat = pipeline.getDhCompat();
        return compat != null && compat.shouldRenderShadows() && DhCompat.hasRenderingEnabled();
    }

    // Draws the opaque or the translucent terrain and restores the shadow pass state that the renderer changes
    public static void render(boolean translucent, ShadowTargets targets) {
        try {
            if (translucent) {
                DhCompat.renderDeferredLods();
            } else {
                DhCompat.renderShadowSolid();
            }
        } finally {
            LWJGL.glUseProgram(0);
            LWJGL.glBindVertexArray(0);
            LWJGL.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            LWJGL.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
            targets.bind();
            GlStateManager.disableCull();
            GlStateManager.disableBlend();
            GlStateManager.depthMask(true);
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlTextureUnits.resetToUnit0();
        }
    }
}
