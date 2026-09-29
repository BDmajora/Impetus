package com.bdmajora.equilibrium.config;

import com.bdmajora.equilibrium.Equilibrium;
import com.bdmajora.equilibrium.mixin.EquilibriumMixinPlugin;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EquilibriumConfigTest {
    @TempDir
    Path dir;

    @AfterEach
    void forgetSharedConfig() {
        Mixins.set(Equilibrium.class, "config", null);
        Mixins.set(EquilibriumMixinPlugin.class, "config", null);
    }

    @Test
    void optionTreeIsConsistent() {
        Map<String, EquilibriumOptions.Entry> entries = EquilibriumOptions.entries();
        assertFalse(entries.isEmpty());
        EquilibriumOptions.Entry inline = EquilibriumOptions.get("mixin.world.inline_block_access");
        assertEquals("mixin.world.inline_block_access", inline.name());
        assertTrue(inline.enabledByDefault());
        assertFalse(inline.description().isEmpty());
        assertEquals("world.inline_block_access", inline.path());
        assertEquals("world", inline.category());
        assertEquals(1, inline.depth());
        assertEquals(Map.of("mixin.util.chunk_access", true), inline.dependencies());
        assertEquals(0, EquilibriumOptions.get("mixin.world").depth());
        assertNull(EquilibriumOptions.get("mixin.nope"));
        List<String> categories = EquilibriumOptions.categories();
        assertTrue(categories.contains("world"));
        assertTrue(categories.contains("util"));
        assertEquals(categories.size(), categories.stream().distinct().count());
        for (String category : categories) {
            for (EquilibriumOptions.Entry entry : EquilibriumOptions.inCategory(category)) {
                assertEquals(category, entry.category());
            }
        }
        // Every entry with vanilla-visible behaviour says so, and every dependency names a real option
        boolean anyNonVanilla = false;
        for (EquilibriumOptions.Entry entry : entries.values()) {
            anyNonVanilla |= entry.nonVanillaBehaviour() != null;
            for (String dependency : entry.dependencies().keySet()) {
                assertNotNull(EquilibriumOptions.get(dependency));
            }
        }
        assertTrue(anyNonVanilla);
    }

    @Test
    void optionsTrackWhoSetThem() {
        Option parent = new Option("mixin.world", true, false);
        Option child = new Option("mixin.world.raycast", true, false);
        assertEquals("mixin.world.raycast", child.getName());
        assertTrue(child.isEnabled());
        assertFalse(child.isOverridden());
        assertFalse(child.isUserDefined());
        assertFalse(child.isModDefined());
        assertTrue(child.getDefiningMods().isEmpty());
        child.setEnabled(false, true);
        assertTrue(child.isUserDefined());
        assertTrue(child.isOverridden());
        child.addModOverride(true, "modA");
        child.addModOverride(true, "modB");
        assertTrue(child.isModDefined());
        assertEquals(List.of("modA", "modB"), List.copyOf(child.getDefiningMods()));
        child.clearModsDefiningValue();
        assertFalse(child.isModDefined());

        EquilibriumConfig config = EquilibriumConfig.load(dir.resolve("fresh.properties"));
        Option world = config.getOption("mixin.world");
        Option raycast = config.getOption("mixin.world.raycast");
        assertSame(world, config.getParent(raycast));
        assertNull(config.getParent(world));
        assertTrue(raycast.isEnabledRecursive(config));
        world.setEnabled(false, true);
        assertTrue(raycast.isEnabled());
        assertFalse(raycast.isEnabledRecursive(config));
        assertFalse(config.isOptionEnabled("mixin.world.raycast"));
        assertFalse(config.isOptionEnabled("mixin.missing"));
        world.setEnabled(true, false);

        Option dependent = new Option("mixin.x", true, false);
        assertFalse(dependent.disableIfDependenciesNotMet(config));
        dependent.addDependency(raycast, false);
        assertTrue(dependent.disableIfDependenciesNotMet(config));
        assertFalse(dependent.isEnabled());
        assertFalse(dependent.disableIfDependenciesNotMet(config));
        Option satisfied = new Option("mixin.y", true, false);
        satisfied.addDependency(raycast, true);
        assertFalse(satisfied.disableIfDependenciesNotMet(config));
    }

    @Test
    void loadingReadsPropertiesAppliesModsAndDependencies() throws Exception {
        Path file = dir.resolve("equilibrium.properties");
        Files.writeString(file, String.join("\n",
                "mixin.util.chunk_access=false",
                "mixin.world.raycast=FALSE",
                "mixin.chunk=true",
                "bogus.key=true",
                "mixin.world.explosions=maybe",
                ""));
        EquilibriumConfig config = EquilibriumConfig.load(file);
        assertEquals(EquilibriumOptions.entries().size(), config.getOptionCount());
        assertFalse(config.isOptionEnabled("mixin.util.chunk_access"));
        assertTrue(config.getOption("mixin.util.chunk_access").isUserDefined());
        assertFalse(config.isOptionEnabled("mixin.world.raycast"));
        assertTrue(config.getOption("mixin.chunk").isUserDefined());
        assertTrue(config.isOptionEnabled("mixin.world.explosions"));
        assertFalse(config.getOption("mixin.world.explosions").isUserDefined());
        // The dependency sweep turns inline block access off because chunk access is off
        assertFalse(config.isOptionEnabled("mixin.world.inline_block_access"));
        assertFalse(config.getOption("mixin.world.inline_block_access").isOverridden());
        // The BetterFps marker on the test classpath forces the sine table off
        Option sine = config.getOption("mixin.math.sine_lut");
        assertFalse(sine.isEnabled());
        assertTrue(sine.isModDefined());
        assertEquals(List.of("BetterFps"), List.copyOf(sine.getDefiningMods()));
        assertEquals(3 + 1, config.getOptionOverrideCount());

        // Saving keeps the user's keys uncommented and everything else as a commented default
        config.save();
        String written = Files.readString(file);
        assertTrue(written.contains("\nmixin.util.chunk_access=false\n"));
        assertTrue(written.contains("\n#mixin.world.explosions=true\n"));
        assertTrue(written.contains("# Requires: mixin.util.chunk_access=true"));
        assertTrue(written.contains("# Differs from vanilla:"));
        EquilibriumConfig reloaded = EquilibriumConfig.load(file);
        assertFalse(reloaded.isOptionEnabled("mixin.util.chunk_access"));

        assertSame(config.getOption("mixin.world.explosions.block_raycast"), config.getEffectiveOptionForMixin("world.explosions.block_raycast.ExplosionMixin"));
        assertSame(config.getOption("mixin.util.chunk_access"), config.getEffectiveOptionForMixin("util.chunk_access.WorldMixin"));
        assertNull(config.getEffectiveOptionForMixin("nowhere.Mixin"));
        // A disabled ancestor short-circuits the walk, so it is the rule reported
        config.setOptionEnabled("mixin.world", false);
        config.setOptionEnabled("mixin.missing", false);
        assertSame(config.getOption("mixin.world"), config.getEffectiveOptionForMixin("world.explosions.block_raycast.ExplosionMixin"));
    }

    @Test
    void missingUnreadableAndUnwritableFilesAreTolerated() throws Exception {
        Path missing = dir.resolve("new.properties");
        EquilibriumConfig config = EquilibriumConfig.load(missing);
        assertTrue(Files.isRegularFile(missing));
        assertTrue(config.isOptionEnabled("mixin.world"));

        Path unreadable = dir.resolve("locked.properties");
        Files.writeString(unreadable, "mixin.world=false\n");
        Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("---------"));
        try {
            EquilibriumConfig locked = EquilibriumConfig.load(unreadable);
            // Root can still read it; otherwise the read error falls back to defaults
            assertEquals(Files.isReadable(unreadable) ? false : true, locked.isOptionEnabled("mixin.world"));
        } finally {
            Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("rw-r--r--"));
        }

        // A directory in place of the file makes both the load-time save and a later save fail quietly
        Path asDirectory = dir.resolve("dir.properties");
        Files.createDirectory(asDirectory);
        EquilibriumConfig unwritable = EquilibriumConfig.load(asDirectory);
        unwritable.save();
        assertTrue(Files.isDirectory(asDirectory));

        // A null file only happens through reflection, and save is then a no-op
        Mixins.set(unwritable, "file", null);
        unwritable.save();
    }

    @Test
    void modOverridesRespectUsersAndEarlierDisables() {
        EquilibriumConfig config = EquilibriumConfig.load(dir.resolve("mods.properties"));
        config.applyModOverride(new ModCompatibility.Override("m", "world.raycast", false));
        Option raycast = config.getOption("mixin.world.raycast");
        assertFalse(raycast.isEnabled());
        assertEquals(List.of("m"), List.copyOf(raycast.getDefiningMods()));
        // A later mod wanting it on loses to the earlier disable
        config.applyModOverride(new ModCompatibility.Override("n", "mixin.world.raycast", true));
        assertFalse(raycast.isEnabled());
        assertEquals(List.of("m"), List.copyOf(raycast.getDefiningMods()));
        // Disabling again resets the contributor list to the newest mod
        config.applyModOverride(new ModCompatibility.Override("o", "mixin.world.raycast", false));
        assertEquals(List.of("m", "o"), List.copyOf(raycast.getDefiningMods()));
        raycast.setEnabled(true, false);
        config.applyModOverride(new ModCompatibility.Override("p", "mixin.world.raycast", false));
        assertEquals(List.of("p"), List.copyOf(raycast.getDefiningMods()));
        // Enabling an enabled option just records the mod
        Option chunk = config.getOption("mixin.chunk");
        config.applyModOverride(new ModCompatibility.Override("q", "mixin.chunk", true));
        assertTrue(chunk.isEnabled());
        assertEquals(List.of("q"), List.copyOf(chunk.getDefiningMods()));
        // The user's explicit choice wins over any mod
        Option explosions = config.getOption("mixin.world.explosions");
        explosions.setEnabled(true, true);
        config.applyModOverride(new ModCompatibility.Override("r", "mixin.world.explosions", false));
        assertTrue(explosions.isEnabled());
        assertFalse(explosions.isModDefined());
        // Unknown options are ignored with a warning
        config.applyModOverride(new ModCompatibility.Override("s", "mixin.nothing", false));
        ModCompatibility.Override override = new ModCompatibility.Override("t", "u", true);
        assertEquals("t", override.modId());
        assertEquals("u", override.option());
        assertTrue(override.enabled());
        List<ModCompatibility.Override> detected = ModCompatibility.detect();
        assertEquals(1, detected.size());
        assertEquals("mixin.math.sine_lut", detected.get(0).option());
    }

    @Test
    void sharedConfigStatisticsAndOverlay() throws Exception {
        Path file = dir.resolve("stats.properties");
        Files.writeString(file, "mixin.util.chunk_access=false\nmixin.world.raycast=false\n");
        EquilibriumConfig config = EquilibriumConfig.load(file);
        Equilibrium.setConfig(config);
        assertSame(config, Equilibrium.config());
        assertFalse(Equilibrium.isEnabled("mixin.world.raycast"));
        assertTrue(Equilibrium.isEnabled("mixin.world"));
        List<String> lines = Equilibrium.statistics();
        assertTrue(lines.get(0).startsWith("Equilibrium: "));
        assertTrue(lines.get(0).endsWith(" of " + config.getOptionCount() + " optimizations active"));
        assertTrue(lines.contains("  Disabled in the config file:"));
        assertTrue(lines.contains("    mixin.world.raycast"));
        assertTrue(lines.contains("  Disabled for mod compatibility:"));
        assertTrue(lines.contains("    mixin.math.sine_lut (BetterFps)"));
        assertTrue(lines.contains("  Disabled because a dependency is off:"));
        assertTrue(lines.contains("    mixin.world.inline_block_access"));
        String overlay = Equilibrium.debugOverlayLine();
        assertTrue(overlay.matches("Equilibrium: \\d+/" + config.getOptionCount() + " active \\(/equilibrium for detail\\)"));

        // With nothing set the lazy loader reads the default file under the working directory
        Mixins.set(Equilibrium.class, "config", null);
        EquilibriumConfig loaded = Equilibrium.config();
        assertNotNull(loaded);
        assertTrue(Files.isRegularFile(EquilibriumConfig.defaultFile()));
        assertTrue(Equilibrium.statistics().stream().noneMatch(line -> line.contains("Disabled in the config file")));
    }

    @Test
    void mixinPluginGatesOnTheOptionTree() {
        EquilibriumMixinPlugin plugin = new EquilibriumMixinPlugin();
        plugin.onLoad("com.bdmajora.equilibrium.mixin");
        EquilibriumConfig config = Mixins.get(EquilibriumMixinPlugin.class, "config");
        assertNotNull(config);
        assertSame(config, Equilibrium.config());
        plugin.onLoad("com.bdmajora.equilibrium.mixin");
        assertSame(config, Mixins.get(EquilibriumMixinPlugin.class, "config"));
        assertTrue(plugin.shouldApplyMixin("net.minecraft.world.World", "com.bdmajora.equilibrium.mixin.world.raycast.WorldMixin"));
        assertFalse(plugin.shouldApplyMixin("net.minecraft.world.World", "com.bdmajora.equilibrium.mixin.math.sine_lut.MathHelperMixin"));
        assertFalse(plugin.shouldApplyMixin("net.minecraft.world.World", "com.bdmajora.equilibrium.mixin.unknown.Mixin"));
        assertFalse(plugin.shouldApplyMixin("net.minecraft.world.World", "com.example.Foreign"));
        assertNull(plugin.getRefMapperConfig());
        assertNull(plugin.getMixins());
        plugin.acceptTargets(null, null);
        plugin.preApply(null, null, null, null);
        plugin.postApply(null, null, null, null);

        // The kill switch is read once at class init, so it is flipped underneath the plugin here
        Statics.set(EquilibriumMixinPlugin.class, "DISABLE_ALL_MIXINS", true);
        try {
            Mixins.set(EquilibriumMixinPlugin.class, "config", null);
            plugin.onLoad("com.bdmajora.equilibrium.mixin");
            assertNull(Mixins.get(EquilibriumMixinPlugin.class, "config"));
            assertFalse(plugin.shouldApplyMixin("net.minecraft.world.World", "com.bdmajora.equilibrium.mixin.world.raycast.WorldMixin"));
        } finally {
            Statics.set(EquilibriumMixinPlugin.class, "DISABLE_ALL_MIXINS", false);
        }
    }
}
