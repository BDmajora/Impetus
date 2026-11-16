package com.bdmajora.impetus.umbra.gl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Texture units reserved by the pipeline.
public final class GlTextureUnits {
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Number of units whose bindings are cached.
    public static final int CACHED_UNITS = 8;

    // Prevents instantiation
    private GlTextureUnits() {
    }

    // Selects unit zero.
    public static void resetToUnit0() {
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + 1);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
    }

    // Selects a scratch unit.
    public static void selectScratch(int unit) {
        if (unit < 0) {
            LOGGER.error("[Umbra] Ignoring texture-unit selection for invalid unit {}", unit);
            return;
        }
        if (unit < CACHED_UNITS) {
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + unit);
        } else {
            LWJGL.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        }
    }

    // Returns from the scratch unit to unit zero.
    public static void releaseScratch() {
        resetToUnit0();
    }

    // Binds a texture to a unit inside a binding run.
    public static void bindTextureInRun(int unit, int target, int texture) {
        if (unit < 0) {
            LOGGER.error("[Umbra] Ignoring texture bind for invalid unit {}", unit);
            return;
        }
        selectScratch(unit);
        if (unit < CACHED_UNITS && target == GL11.GL_TEXTURE_2D) {
            GlStateManager.bindTexture(texture);
        } else {
            LWJGL.glBindTexture(target, texture);
        }
    }

    // Binds a 2D texture to a unit without the cache.
    public static void forceBindTexture2D(int unit, int texture) {
        if (unit >= CACHED_UNITS) {
            bindTexture2D(unit, texture);
            return;
        }
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + unit);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GlStateManager.bindTexture(texture);
        resetToUnit0();
    }

    // Binds a 2D texture to a unit when it changed.
    public static void bindTexture2D(int unit, int texture) {
        if (unit < CACHED_UNITS) {
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + unit);
            GlStateManager.bindTexture(texture);
            resetToUnit0();
        } else {
            selectScratch(unit);
            LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            releaseScratch();
        }
    }
}
