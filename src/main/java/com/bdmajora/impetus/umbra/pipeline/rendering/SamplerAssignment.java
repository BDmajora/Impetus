package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.pipeline.CustomTextureManager;

import java.util.BitSet;
import java.util.Collections;
import java.util.Map;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Points a program's sampler uniforms at their fixed units (OptiFine's setProgramUniform1i) with the stage's custom-texture overrides, and holds the gbuffers-stage overrides of the live pipeline for the terrain and DH programs built outside it
public final class SamplerAssignment {
    private static final BitSet NONE_FLIPPED = new BitSet();
    private static final Published NOTHING = new Published(null, Collections.emptyMap());

    // The overrides the live pipeline published, with the pipeline that owns them so a stale pipeline's teardown cannot wipe its successor's
    private record Published(Object owner, Map<String, CustomTextureManager.Override> overrides) {
    }

    private static volatile Published active = NOTHING;

    private SamplerAssignment() {
    }

    // Publishes the gbuffers/shadow-stage overrides; owner identifies the pipeline for withdraw
    public static void publish(Object owner, Map<String, CustomTextureManager.Override> overrides) {
        active = new Published(owner, overrides);
    }

    // Drops the published overrides, only if this owner still holds them
    public static void withdraw(Object owner) {
        if (active.owner() == owner) {
            active = NOTHING;
        }
    }

    // The overrides currently in force, empty with no pipeline
    public static Map<String, CustomTextureManager.Override> activeOverrides() {
        return active.overrides();
    }

    // The standard gbuffer mapping on the CURRENTLY BOUND raw program with the live pipeline's overrides; used by the Impetus terrain and shadow overrides and the DH programs
    public static void assignToBoundProgram(int programId) {
        assign(programId, SamplerLayout.gbuffer(), active.overrides(), NONE_FLIPPED);
    }

    // A colortex override is skipped once an earlier pass flipped that buffer, matching Umbra; the program must be bound
    public static void assign(int programId, Map<String, Integer> units,
                              Map<String, CustomTextureManager.Override> overrides, BitSet flippedAtLeastOnce) {
        boolean waterShadow = LWJGL.glGetUniformLocation(programId, "watershadow") != -1;
        int shadowAliasUnit = SamplerLayout.isGbufferLayout(units)
                ? SamplerLayout.GBUFFER_SHADOW_TEX_1_UNIT : SamplerLayout.SHADOW_TEX_1_UNIT;
        for (Map.Entry<String, Integer> entry : units.entrySet()) {
            int location = LWJGL.glGetUniformLocation(programId, entry.getKey());
            if (location == -1) {
                continue;
            }
            int unit = entry.getValue();
            if (waterShadow && "shadow".equals(entry.getKey())) {
                // UmbraSamplers.addShadowSamplers parity: with watershadow present the legacy shadow alias reads the pre-translucent depth (shadowtex1) while watershadow reads shadowtex0
                unit = shadowAliasUnit;
            }
            CustomTextureManager.Override override = overrides.get(entry.getKey());
            if (override != null && (override.colorTarget < 0 || !flippedAtLeastOnce.get(override.colorTarget))) {
                unit = override.unit;
            }
            LWJGL.glUniform1i(location, unit);
        }
        // Pack-declared sampler names with no standard unit (customTexture.<name> directives)
        for (Map.Entry<String, CustomTextureManager.Override> entry : overrides.entrySet()) {
            if (units.containsKey(entry.getKey())) {
                continue;
            }
            int location = LWJGL.glGetUniformLocation(programId, entry.getKey());
            if (location != -1) {
                LWJGL.glUniform1i(location, entry.getValue().unit);
            }
        }
    }
}
