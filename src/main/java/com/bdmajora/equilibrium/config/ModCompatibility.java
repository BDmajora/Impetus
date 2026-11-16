package com.bdmajora.equilibrium.config;

import com.bdmajora.equilibrium.Equilibrium;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Switches that other mods force off.
public final class ModCompatibility {
    // Value that a mod forces.
    public static final class Override {
        // Mod that forces the value.
        private final String modId;
        // Name of the switch.
        private final String option;
        // Forced value.
        private final boolean enabled;

        // Creates an override.
        Override(String modId, String option, boolean enabled) {
            this.modId = modId;
            this.option = option;
            this.enabled = enabled;
        }

        // Returns the mod.
        public String modId() {
            return this.modId;
        }

        // Returns the switch.
        public String option() {
            return this.option;
        }

        // Returns the forced value.
        public boolean enabled() {
            return this.enabled;
        }
    }

    // Mod that conflicts with some switches.
    private static final class Conflict {
        // Name of the mod.
        final String modId;
        // Class that shows the mod is present.
        final String marker;
        // Reason of the conflict.
        final String reason;
        // Switches to turn off.
        final String[] disables;

        // Creates a conflict.
        Conflict(String modId, String marker, String reason, String... disables) {
            this.modId = modId;
            this.marker = marker;
            this.reason = reason;
            this.disables = disables;
        }
    }

    // Known conflicts.
    private static final List<Conflict> CONFLICTS = conflicts();

    // Prevents instantiation
    private ModCompatibility() {
    }

    // Returns the known conflicts.
    private static List<Conflict> conflicts() {
        Map<String, Conflict> conflicts = new LinkedHashMap<>();

        conflicts.put("cubicchunks", new Conflict("Cubic Chunks",
                "io.github.opencubicchunks.cubicchunks.core.asm.CubicChunksCoreContainer",
                "it replaces the chunk column with a cube grid",
                "mixin.chunk",
                "mixin.util.chunk_access",
                "mixin.world.inline_block_access",
                "mixin.world.inline_height",
                "mixin.world.explosions",
                "mixin.entity.collisions",
                "mixin.entity.fast_retrieval"));

        conflicts.put("sponge", new Conflict("SpongeForge",
                "org.spongepowered.common.SpongeImpl",
                "it rewrites world ticking and inventory handling",
                "mixin.world.tick_scheduler",
                "mixin.block.hopper",
                "mixin.world.block_entity_ticking",
                "mixin.world.explosions"));

        conflicts.put("betterfps", new Conflict("BetterFps",
                "guichaguri.betterfps.tweaker.BetterFpsTweaker",
                "its math transformer replaces the same sine table",
                "mixin.math.sine_lut"));

        conflicts.put("foamfix", new Conflict("FoamFix",
                "pl.asie.foamfix.coremod.FoamFixCore",
                "its coremod rewrites BlockPos",
                "mixin.math.fast_blockpos"));

        return Collections.unmodifiableList(new ArrayList<>(conflicts.values()));
    }

    // Returns the overrides of every present mod.
    public static List<Override> detect() {
        List<Override> overrides = new ArrayList<>();

        for (Conflict conflict : CONFLICTS) {
            if (!isClassPresent(conflict.marker)) {
                continue;
            }

            Equilibrium.LOGGER.warn("{} was detected; {}. The following options will be disabled: {}",
                    conflict.modId, conflict.reason, String.join(", ", conflict.disables));

            for (String option : conflict.disables) {
                overrides.add(new Override(conflict.modId, option, false));
            }
        }

        return overrides;
    }

    // Returns whether the class can be found.
    private static boolean isClassPresent(String name) {
        try {
            Class.forName(name, false, ModCompatibility.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
