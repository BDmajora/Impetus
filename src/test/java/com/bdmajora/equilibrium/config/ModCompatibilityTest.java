package com.bdmajora.equilibrium.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

// Valkyrien Skies against Equilibrium's World overwrites, through a loader that shows only VS's marker.
class ModCompatibilityTest {
    private static final String VS_MARKER = "org/valkyrienskies/mod/common/ValkyrienSkiesMod.class";
    private static final Set<String> VS_SWITCHES = Set.of("mixin.entity.fast_retrieval", "mixin.world.explosions.entity_raycast");

    @TempDir
    Path dir;

    // Shows the given class files and refuses to load any class, so detection must stay a resource lookup.
    private static class MarkerLoader extends ClassLoader {
        private final Set<String> files;

        MarkerLoader(ClassLoader parent, String... files) {
            super(parent);
            this.files = Set.of(files);
        }

        @Override
        public URL getResource(String name) {
            return this.files.contains(name) ? ModCompatibilityTest.class.getResource("ModCompatibilityTest.class") : super.getResource(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.valkyrienskies.")) {
                throw new AssertionError("detection loaded " + name);
            }
            return super.loadClass(name, resolve);
        }
    }

    private static Set<String> options(List<ModCompatibility.Override> overrides, String mod) {
        return overrides.stream().filter(o -> o.modId().equals(mod)).map(ModCompatibility.Override::option).collect(Collectors.toSet());
    }

    @Test
    void valkyrienSkiesTurnsOffTheTwoOverwritesItShares() {
        List<ModCompatibility.Override> detected = ModCompatibility.detect(new MarkerLoader(null, VS_MARKER));
        assertEquals(VS_SWITCHES, options(detected, "Valkyrien Skies"));
        // Only VS was visible, and every override turns its switch off
        assertEquals(2, detected.size());
        assertTrue(detected.stream().noneMatch(ModCompatibility.Override::enabled));
    }

    @Test
    void theSharedClassPathStillSeesOnlyBetterFps() {
        // VS is not on the test class path; its entry must not fire there or the other config tests shift
        List<ModCompatibility.Override> shared = ModCompatibility.detect();
        assertTrue(options(shared, "Valkyrien Skies").isEmpty());
        // A loader on top of the class path adds VS to the BetterFps marker the tests ship
        List<ModCompatibility.Override> both = ModCompatibility.detect(new MarkerLoader(ModCompatibilityTest.class.getClassLoader(), VS_MARKER));
        assertEquals(VS_SWITCHES, options(both, "Valkyrien Skies"));
        assertEquals(Set.of("mixin.math.sine_lut"), options(both, "BetterFps"));
    }

    @Test
    void aMissingOrNullLoaderFindsNothing() {
        assertTrue(ModCompatibility.detect(null).isEmpty());
        assertTrue(ModCompatibility.detect(new MarkerLoader(null)).isEmpty());
        // The VS coremod or a VS2 class is not the marker; only the mod class counts
        assertTrue(ModCompatibility.detect(new MarkerLoader(null, "org/valkyrienskies/mixin/MixinLoaderForge.class",
                "org/valkyrienskies/mod/common/ValkyrienSkiesMod.java")).isEmpty());
    }

    @Test
    void everyConflictNamesARealSwitch() {
        // A typo in a switch name would be accepted and turn nothing off, so each one must exist
        Set<String> known = EquilibriumOptions.entries().keySet();
        List<ModCompatibility.Override> all = ModCompatibility.detect(new MarkerLoader(null, VS_MARKER,
                "io/github/opencubicchunks/cubicchunks/core/asm/CubicChunksCoreContainer.class",
                "org/spongepowered/common/SpongeImpl.class",
                "guichaguri/betterfps/tweaker/BetterFpsTweaker.class",
                "pl/asie/foamfix/coremod/FoamFixCore.class"));
        assertEquals(Set.of("Valkyrien Skies", "Cubic Chunks", "SpongeForge", "BetterFps", "FoamFix"),
                all.stream().map(ModCompatibility.Override::modId).collect(Collectors.toSet()));
        for (ModCompatibility.Override override : all) {
            assertTrue(known.contains(override.option()), override.modId() + " names unknown switch " + override.option());
        }
    }

    @Test
    void theOverridesDisableExactlyTheConflictingMixins() throws Exception {
        EquilibriumConfig config = EquilibriumConfig.load(dir.resolve("equilibrium.properties"));
        for (ModCompatibility.Override override : ModCompatibility.detect(new MarkerLoader(null, VS_MARKER))) {
            config.applyModOverride(override);
        }
        com.bdmajora.testing.Mixins.call(config, "applyDependencies");
        for (String option : VS_SWITCHES) {
            assertFalse(config.isOptionEnabled(option), option);
            assertEquals(List.of("Valkyrien Skies"), List.copyOf(config.getOption(option).getDefiningMods()));
        }
        // The two mixin classes VS collides with are off, their neighbours stay on
        assertFalse(config.getEffectiveOptionForMixin("entity.fast_retrieval.WorldMixin").isEnabled());
        assertFalse(config.getEffectiveOptionForMixin("world.explosions.entity_raycast.WorldMixin").isEnabled());
        assertTrue(config.getEffectiveOptionForMixin("world.explosions.block_raycast.ExplosionMixin").isEnabled());
        assertTrue(config.isOptionEnabled("mixin.world.raycast"));
        assertTrue(config.isOptionEnabled("mixin.entity.collisions.movement"));
        assertTrue(config.isOptionEnabled("mixin.util.chunk_access"));
    }

    @Test
    void aSwitchTheUserForcedOnStaysOnAgainstVs() throws Exception {
        Path file = dir.resolve("forced.properties");
        Files.writeString(file, "mixin.entity.fast_retrieval=true\n");
        EquilibriumConfig config = EquilibriumConfig.load(file);
        for (ModCompatibility.Override override : ModCompatibility.detect(new MarkerLoader(null, VS_MARKER))) {
            config.applyModOverride(override);
        }
        // The config file wins and is only warned about; the other switch still yields
        assertTrue(config.isOptionEnabled("mixin.entity.fast_retrieval"));
        assertTrue(config.getOption("mixin.entity.fast_retrieval").isUserDefined());
        assertFalse(config.isOptionEnabled("mixin.world.explosions.entity_raycast"));
    }

    private static EquilibriumConfig loadWithVs(Path file) {
        EquilibriumConfig config = EquilibriumConfig.load(file);
        for (ModCompatibility.Override override : ModCompatibility.detect(new MarkerLoader(null, VS_MARKER))) {
            config.applyModOverride(override);
        }
        com.bdmajora.testing.Mixins.call(config, "applyDependencies");
        return config;
    }

    @Test
    void detectionIsRepeatableAndHasNoDuplicates() {
        MarkerLoader loader = new MarkerLoader(null, VS_MARKER);
        List<ModCompatibility.Override> first = ModCompatibility.detect(loader);
        List<ModCompatibility.Override> second = ModCompatibility.detect(loader);
        // No state carries over between calls, and each switch appears once
        assertEquals(first.stream().map(ModCompatibility.Override::option).toList(),
                second.stream().map(ModCompatibility.Override::option).toList());
        assertEquals(first.size(), first.stream().map(ModCompatibility.Override::option).distinct().count());
        // The returned list is the caller's to keep
        assertDoesNotThrow(() -> first.add(new ModCompatibility.Override("x", "y", true)));
        assertEquals(2, ModCompatibility.detect(loader).size());
    }

    @Test
    void aUserWhoAlreadyTurnedTheSwitchesOffKeepsTheirSetting() throws Exception {
        Path file = dir.resolve("off.properties");
        Files.writeString(file, "mixin.entity.fast_retrieval=false\nmixin.world.explosions.entity_raycast=false\n");
        EquilibriumConfig config = loadWithVs(file);
        for (String option : VS_SWITCHES) {
            assertFalse(config.isOptionEnabled(option), option);
            // The file decided it, so VS is not credited and the file is not rewritten as a mod default
            assertTrue(config.getOption(option).isUserDefined(), option);
            assertTrue(config.getOption(option).getDefiningMods().isEmpty(), option);
        }
    }

    @Test
    void aUserWhoForcedBothOnKeepsBoth() throws Exception {
        Path file = dir.resolve("on.properties");
        Files.writeString(file, "mixin.entity.fast_retrieval=true\nmixin.world.explosions.entity_raycast=true\n");
        EquilibriumConfig config = loadWithVs(file);
        for (String option : VS_SWITCHES) {
            assertTrue(config.isOptionEnabled(option), option);
        }
    }

    @Test
    void aParentSwitchTheUserTurnedOffStillWins() throws Exception {
        // mixin.world.explosions off covers entity_raycast whatever VS asks
        Path file = dir.resolve("parent.properties");
        Files.writeString(file, "mixin.world.explosions=false\n");
        EquilibriumConfig config = loadWithVs(file);
        assertFalse(config.getEffectiveOptionForMixin("world.explosions.entity_raycast.WorldMixin").isEnabled());
        assertFalse(config.getEffectiveOptionForMixin("world.explosions.block_raycast.ExplosionMixin").isEnabled());
        assertFalse(config.getEffectiveOptionForMixin("entity.fast_retrieval.WorldMixin").isEnabled());
    }

    @Test
    void cubicChunksAndVsTogetherStillTurnFastRetrievalOffOnce() throws Exception {
        // Both mods name mixin.entity.fast_retrieval; the switch ends up off and credits both
        EquilibriumConfig config = EquilibriumConfig.load(dir.resolve("both.properties"));
        for (ModCompatibility.Override override : ModCompatibility.detect(new MarkerLoader(null, VS_MARKER,
                "io/github/opencubicchunks/cubicchunks/core/asm/CubicChunksCoreContainer.class"))) {
            config.applyModOverride(override);
        }
        Option option = config.getOption("mixin.entity.fast_retrieval");
        assertFalse(option.isEnabled());
        assertEquals(Set.of("Cubic Chunks", "Valkyrien Skies"), Set.copyOf(option.getDefiningMods()));
    }

    @Test
    void theVsOverridesSurviveASaveAndReload() throws Exception {
        Path file = dir.resolve("saved.properties");
        EquilibriumConfig config = loadWithVs(file);
        config.save();
        String written = Files.readString(file);
        // Mod decisions are not written as user choices, so removing VS brings the switches straight back
        assertFalse(written.contains("\nmixin.entity.fast_retrieval=false"));
        assertFalse(written.contains("\nmixin.world.explosions.entity_raycast=false"));
        EquilibriumConfig withoutVs = EquilibriumConfig.load(file);
        for (String option : VS_SWITCHES) {
            assertTrue(withoutVs.isOptionEnabled(option), option);
        }
    }

    @Test
    void aLoaderWhoseLookupThrowsCountsAsAbsent() {
        // Detection runs while coremods load; a throwing class path lookup must not end the launch
        ClassLoader broken = new ClassLoader(null) {
            @Override
            public URL getResource(String name) {
                throw new IllegalStateException("broken " + name);
            }
        };
        assertTrue(assertDoesNotThrow(() -> ModCompatibility.detect(broken)).isEmpty());
        // One marker throwing does not hide another that resolves
        ClassLoader partly = new MarkerLoader(null, VS_MARKER) {
            @Override
            public URL getResource(String name) {
                if (name.startsWith("io/github/opencubicchunks/")) {
                    throw new IllegalStateException("broken " + name);
                }
                return super.getResource(name);
            }
        };
        assertEquals(VS_SWITCHES, options(ModCompatibility.detect(partly), "Valkyrien Skies"));
    }
}
