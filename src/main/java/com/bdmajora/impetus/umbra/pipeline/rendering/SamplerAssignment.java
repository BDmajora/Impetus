package com.bdmajora.impetus.umbra.pipeline.rendering;

import com.bdmajora.impetus.umbra.pipeline.CustomTextureManager;

import java.util.BitSet;
import java.util.Collections;
import java.util.Map;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Published custom texture overrides of the active pipeline.
public final class SamplerAssignment {
    // Empty flip set.
    private static final BitSet NONE_FLIPPED = new BitSet();
    // Publication without an owner or overrides.
    private static final Published NOTHING = new Published(null, Collections.emptyMap());

    // Data of the published.
    private record Published(Object owner, Map<String, CustomTextureManager.Override> overrides) {
    }

    // Current publication.
    private static volatile Published active = NOTHING;

    // Prevents instantiation
    private SamplerAssignment() {
    }

    // Publishes the overrides of an owner.
    public static void publish(Object owner, Map<String, CustomTextureManager.Override> overrides) {
        active = new Published(owner, overrides);
    }

    // Withdraws the overrides of an owner.
    public static void withdraw(Object owner) {
        if (active.owner() == owner) {
            active = NOTHING;
        }
    }

    // Returns the active overrides.
    public static Map<String, CustomTextureManager.Override> activeOverrides() {
        return active.overrides();
    }

    // Assigns sampler units to the bound program.
    public static void assignToBoundProgram(int programId) {
        assign(programId, SamplerLayout.gbuffer(), active.overrides(), NONE_FLIPPED);
    }

    // Assigns sampler units to a program.
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
                unit = shadowAliasUnit;
            }
            CustomTextureManager.Override override = overrides.get(entry.getKey());
            if (override != null && (override.colorTarget < 0 || !flippedAtLeastOnce.get(override.colorTarget))) {
                unit = override.unit;
            }
            LWJGL.glUniform1i(location, unit);
        }
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
