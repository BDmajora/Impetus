package com.bdmajora.equilibrium.config;

import com.bdmajora.equilibrium.Equilibrium;
import com.bdmajora.equilibrium.mixin.EquilibriumMixinPlugin;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
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

    // Checks that every mixin switch still gates a listed mixin and every listed mixin has a switch
    @Test
    void everyMixinSwitchGatesAListedMixin() throws Exception {
        List<String> mixins = new ArrayList<>();
        try (InputStream in = EquilibriumConfigTest.class.getResourceAsStream("/mixins.equilibrium.json")) {
            assertNotNull(in);
            com.google.gson.JsonObject json = com.google.gson.JsonParser
                    .parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String section : List.of("mixins", "client", "server")) {
                if (json.has(section)) {
                    json.getAsJsonArray(section).forEach(element -> mixins.add(element.getAsString()));
                }
            }
        }
        assertFalse(mixins.isEmpty());
        EquilibriumConfig config = EquilibriumConfig.load(dir.resolve("switches.properties"));
        for (String name : EquilibriumOptions.entries().keySet()) {
            if (!name.startsWith("mixin.")) {
                continue;
            }
            String prefix = name.substring("mixin.".length()) + ".";
            assertTrue(mixins.stream().anyMatch(mixin -> mixin.startsWith(prefix)), name + " gates no mixin");
        }
        for (String mixin : mixins) {
            assertNotNull(config.getEffectiveOptionForMixin(mixin), mixin + " has no switch");
            // Checks each package has a switch.
            String pack = mixin.substring(0, mixin.lastIndexOf('.'));
            assertNotNull(EquilibriumOptions.get("mixin." + pack), mixin + " only inherits its category's switch");
        }
        // The non-vanilla look task removal is opt in
        assertFalse(EquilibriumOptions.get("mixin.ai.remove_look_tasks").enabledByDefault());
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
        // Checks the entries and dependencies.
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
        // Checks the dependency sweep.
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
        // Checks a disabled ancestor.
        config.setOptionEnabled("mixin.world", false);
        config.setOptionEnabled("mixin.missing", false);
        assertSame(config.getOption("mixin.world"), config.getEffectiveOptionForMixin("world.explosions.block_raycast.ExplosionMixin"));
    }

    @Test
    void aConfigFromBeforeThePrimerCacheWasRemovedStillLoads() throws Exception {
        // A 1.0.2 file still carries mixin.worldgen.primer_state_cache; it is ignored and its neighbours keep their values
        Path old = dir.resolve("old.properties");
        Files.writeString(old, String.join("\n",
                "mixin.worldgen.primer_state_cache=false",
                "mixin.worldgen.chunk_copy=false",
                "mixin.worldgen.int_cache=true",
                "mixin.worldgen.primer_state_cache.extra=true",
                "") );
        EquilibriumConfig config = EquilibriumConfig.load(old);
        assertNull(config.getOption("mixin.worldgen.primer_state_cache"));
        assertFalse(config.isOptionEnabled("mixin.worldgen.chunk_copy"));
        assertTrue(config.isOptionEnabled("mixin.worldgen.int_cache"));
        assertTrue(config.isOptionEnabled("mixin.worldgen"));
        // Loading never rewrites an existing file, so the stale line stays until the user saves from the GUI
        assertTrue(Files.readString(old).contains("primer_state_cache"));
        config.save();
        String saved = Files.readString(old);
        assertFalse(saved.contains("primer_state_cache"), saved);
        assertTrue(saved.contains("mixin.worldgen.chunk_copy=false"), saved);
        // A mod override naming the removed switch is ignored rather than throwing
        config.applyModOverride(new ModCompatibility.Override("somemod", "worldgen.primer_state_cache", false));
        assertNull(config.getOption("mixin.worldgen.primer_state_cache"));
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
            // Checks an unreadable file.
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

        // Checks save without a file.
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

        // Flips the kill switch.
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
