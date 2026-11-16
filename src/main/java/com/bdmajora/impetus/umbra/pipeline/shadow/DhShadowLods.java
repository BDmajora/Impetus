package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.compat.dh.DhCompat;
import com.bdmajora.impetus.umbra.gl.GlTextureUnits;
import com.bdmajora.impetus.umbra.pipeline.UmbraRenderingPipeline;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Drawer of Distant Horizons LODs into the shadow map.
public final class DhShadowLods {
    // Prevents instantiation
    private DhShadowLods() {
    }

    // Returns whether LOD shadows are drawn.
    public static boolean active() {
        UmbraRenderingPipeline pipeline = Umbra.getRenderingPipeline();
        if (pipeline == null) {
            return false;
        }
        DhCompat compat = pipeline.getDhCompat();
        return compat != null && compat.shouldRenderShadows() && DhCompat.hasRenderingEnabled();
    }

    // Draws the solid or translucent LODs.
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
