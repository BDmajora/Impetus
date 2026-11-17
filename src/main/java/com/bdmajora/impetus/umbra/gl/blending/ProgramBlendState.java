package com.bdmajora.impetus.umbra.gl.blending;

import net.minecraft.client.renderer.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.umbra.shaderpack.ShaderProperties;
import com.bdmajora.impetus.umbra.targets.UmbraRenderTargets;
import org.lwjgl.opengl.GL11;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import java.util.Locale;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Blend override of a program for all buffers and for single buffers.
public final class ProgramBlendState {
    // Logger of this class.
    private static final Logger LOGGER = LogManager.getLogger("Impetus/Umbra");
    // Flag set after the missing per buffer blend warning.
    private static boolean warnedNoBufferBlend;

    // Flag set when a base mode is given.
    private final boolean baseSpecified;
    // Blend mode of every buffer or null.
    private final BlendMode baseMode;
    // Blend modes by buffer.
    private final Int2ObjectMap<BlendMode> perTargetModes;

    // Creates a program blend state from its base specified and base mode and per target modes.
    private ProgramBlendState(boolean baseSpecified, BlendMode baseMode, Int2ObjectMap<BlendMode> perTargetModes) {
        this.baseSpecified = baseSpecified;
        this.baseMode = baseMode;
        this.perTargetModes = perTargetModes;
    }

    // State without overrides.
    private static final ProgramBlendState EMPTY = new ProgramBlendState(false, null, Int2ObjectMaps.emptyMap());

    // Returns the state without overrides.
    public static ProgramBlendState empty() {
        return EMPTY;
    }

    // Reads the blend directives of a program on top of vanilla's blending.
    public static ProgramBlendState from(ShaderProperties properties, String programName) {
        return from(properties, programName, null);
    }

    // Reads the blend directives of a program over the given base mode.
    public static ProgramBlendState from(ShaderProperties properties, String programName, BlendMode defaultBase) {
        boolean baseSpecified = false;
        BlendMode baseMode = null;
        String base = properties.getBlendModeOverride(programName).orElse(null);
        if (base != null) {
            baseSpecified = true;
            baseMode = parseMode(programName, "blend." + programName, base);
        } else if (defaultBase != null) {
            baseSpecified = true;
            baseMode = defaultBase;
        }

        Int2ObjectMap<BlendMode> perTargetModes = new Int2ObjectLinkedOpenHashMap<>();
        String prefix = "blend." + programName + ".";
        properties.asMap().forEach((key, value) -> {
            if (!key.startsWith(prefix)) {
                return;
            }
            String targetName = key.substring(prefix.length());
            int target = parseTarget(targetName);
            if (target < 0) {
                LOGGER.warn("[Umbra] Unknown blend target '{}', ignoring {}", targetName, key);
                return;
            }
            perTargetModes.put(target, parseMode(programName, key, value));
        });

        return new ProgramBlendState(baseSpecified, baseMode, perTargetModes);
    }

    // Returns whether the pack set any blend directive for the program.
    public boolean hasDirectives() {
        return this.baseSpecified || !this.perTargetModes.isEmpty();
    }

    // Applies the overrides to the draw buffers.
    public void apply(int[] drawBuffers) {
        if (!hasDirectives()) {
            return;
        }

        if (this.baseSpecified) {
            if (this.baseMode == null) {
                GlStateManager.disableBlend();
            } else {
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(
                        this.baseMode.srcRgb(), this.baseMode.dstRgb(),
                        this.baseMode.srcAlpha(), this.baseMode.dstAlpha());
            }
        }

        if (this.perTargetModes.isEmpty()) {
            if (this.baseSpecified && LWJGL.supportsBufferBlending()) {
                for (int slot = 0; slot < drawBuffers.length; slot++) {
                    applySlotMode(slot, this.baseMode);
                }
            }
            return;
        }
        if (!LWJGL.supportsBufferBlending()) {
            if (!warnedNoBufferBlend) {
                LOGGER.warn("[Umbra] Shader pack requested per-buffer blending, but this GL context does not support it");
                warnedNoBufferBlend = true;
            }
            return;
        }

        for (int slot = 0; slot < drawBuffers.length; slot++) {
            int target = drawBuffers[slot];

            if (this.perTargetModes.containsKey(target)) {
                applySlotMode(slot, this.perTargetModes.get(target));
            } else if (this.baseSpecified) {
                applySlotMode(slot, this.baseMode);
            }
        }
    }

    // Applies the slot mode.
    private static void applySlotMode(int slot, BlendMode mode) {
        if (mode == null) {
            LWJGL.glDisablei(GL11.GL_BLEND, slot);
        } else {
            LWJGL.glEnablei(GL11.GL_BLEND, slot);
            LWJGL.glBlendFuncSeparatei(slot, mode.srcRgb(), mode.dstRgb(), mode.srcAlpha(), mode.dstAlpha());
        }
    }

    // Parses one blend mode and logs a bad one.
    private static BlendMode parseMode(String programName, String key, String value) {
        if ("off".equals(value.trim().toLowerCase(Locale.ROOT))) {
            return null;
        }
        try {
            return BlendMode.parse(value);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("[Umbra] Invalid blend directive for '{}': {} = {} ({})",
                    programName, key, value, e.getMessage());
            return null;
        }
    }

    // Returns the buffer index of a target name or minus one.
    private static int parseTarget(String name) {
        Integer index = UmbraRenderTargets.colorTargetIndex(name.toLowerCase(Locale.ROOT));
        return index != null ? index : -1;
    }
}
