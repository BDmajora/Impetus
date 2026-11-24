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

        // VS overwrites getEntitiesInAABBexcluding to also search the ship under the box, and redirects getBlockDensity's
        // rayTraceBlocks so explosions see ship blocks. Equilibrium overwrites both at the same priority, so one side silently
        // replaced the other: ship entities went missing from lookups, or explosion exposure ignored ships.
        conflicts.put("valkyrienskies", new Conflict("Valkyrien Skies",
                "org.valkyrienskies.mod.common.ValkyrienSkiesMod",
                "it overwrites the same entity lookup and explosion exposure raycast to make them see ships",
                "mixin.entity.fast_retrieval",
                "mixin.world.explosions.entity_raycast"));

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
        return detect(ModCompatibility.class.getClassLoader());
    }

    // Detection against one loader, so tests can show a marker without putting it on the shared class path.
    static List<Override> detect(ClassLoader loader) {
        List<Override> overrides = new ArrayList<>();

        for (Conflict conflict : CONFLICTS) {
            if (!isClassPresent(conflict.marker, loader)) {
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

    // Looks the class file up instead of loading it: a marker can be a @Mod class, and loading one while coremods run
    // would define it before Mixin and the mod's own transformers are ready for it.
    // A lookup that throws counts as absent: the mixin plugin calls this while coremods load, where a throw ends the launch.
    private static boolean isClassPresent(String name, ClassLoader loader) {
        if (loader == null) {
            return false;
        }
        try {
            return loader.getResource(name.replace('.', '/') + ".class") != null;
        } catch (RuntimeException e) {
            Equilibrium.LOGGER.warn("Could not look for {}; treating it as absent", name, e);
            return false;
        }
    }
}
