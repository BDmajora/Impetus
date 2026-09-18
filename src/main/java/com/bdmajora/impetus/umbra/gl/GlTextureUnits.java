package com.bdmajora.impetus.umbra.gl;

import com.bdmajora.impetus.lwjgl.GL11;
import com.bdmajora.impetus.lwjgl.GL13;
import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The one place allowed to move the texture-unit selector or bind a 2D texture: GlStateManager caches against its own idea of the unit, and a raw glActiveTexture/glBindTexture desyncs it into the F2 white-screen bug; units below CACHED_UNITS go through GlStateManager, higher ones raw, and releaseScratch resyncs on unit 0
public final class GlTextureUnits {
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");

    // Length of GlStateManager.textureState on vanilla 1.12.2; indexing at or beyond it THROWS, which is why high units are driven raw (OptiFine patches it to 32, this port runs unpatched)
    public static final int CACHED_UNITS = 8;

    private GlTextureUnits() {
    }

    // Returns the selector to unit 0 with the cache in agreement; stepping through unit 1 FIRST is not redundant, since setActiveTexture is cached and a direct call to 0 can be swallowed while real GL sits on a high unit
    public static void resetToUnit0() {
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + 1);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
    }

    // Selects a unit for raw GL work (mipmaps, glGetTexImage, non-2D binds); must be paired with releaseScratch() in a finally, and prefer a unit at or above CACHED_UNITS (every caller does: 24-33) since a raw BIND on a cached unit is still the caller's problem
    public static void selectScratch(int unit) {
        if (unit < 0) {
            // Unit allocation returns -1 when a pack exhausts the programmable units; routing it here would set activeTextureUnit to -1 and crash at some unrelated bindTexture, so fail where the cause is visible
            LOGGER.error("[Umbra] Ignoring texture-unit selection for invalid unit {}", unit);
            return;
        }
        if (unit < CACHED_UNITS) {
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + unit);
        } else {
            LWJGL.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        }
    }

    // Ends a selectScratch sequence, landing on a selector state the cache agrees with
    public static void releaseScratch() {
        resetToUnit0();
    }

    // Binds one texture as part of a RUN, leaving the selector wherever it lands (finish with releaseScratch()); bindTexture2D restores unit 0 per bind, which for a thirty-sampler program is sixty redundant selector moves, so like Iris's ProgramSamplers.update() the restore is hoisted out of the loop
    public static void bindTextureInRun(int unit, int target, int texture) {
        if (unit < 0) {
            LOGGER.error("[Umbra] Ignoring texture bind for invalid unit {}", unit);
            return;
        }
        selectScratch(unit);
        if (unit < CACHED_UNITS && target == GL11.GL_TEXTURE_2D) {
            // GlStateManager.bindTexture records against its cached active unit, which selectScratch just set through GlStateManager for this range, so cache and real GL agree
            GlStateManager.bindTexture(texture);
        } else {
            // At or above the cache there is no record to keep; below it, a non-2D target is not tracked either.
            LWJGL.glBindTexture(target, texture);
        }
    }

    // Binds a 2D texture on a unit so that REAL GL and GlStateManager's cache both end up on it whatever either believed before: other code binding raw on a cached unit (Distant Horizons puts its block atlas on unit 1 raw) leaves the cache stale, after which a plain cached bind of the same id is skipped and the unit keeps the stranger's texture
    public static void forceBindTexture2D(int unit, int texture) {
        if (unit >= CACHED_UNITS) {
            bindTexture2D(unit, texture);
            return;
        }
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0 + unit);
        LWJGL.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        // Records the id when the cache disagreed, a no-op when it already agreed; real GL is right either way
        GlStateManager.bindTexture(texture);
        resetToUnit0();
    }

    // Binds a 2D texture to a unit and leaves the selector on unit 0 with the cache correct; goes through GlStateManager for cached units and raw beyond, since no cached slot describes those
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
