package com.bdmajora.impetus.core;

import com.bdmajora.testing.LaunchEnvironment;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import com.bdmajora.impetus.mixin.ImpetusVintageMixinPlugin;
import com.google.gson.Gson;
import org.spongepowered.asm.mixin.transformer.Config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HbmCompatTest {
    // NTM CE 2.6.1.0's hbm.vanilla.mixin.json client list, in its order.
    private static final List<String> NTM_2610_CLIENT = List.of("base.MixinBufferBuilder",
            "betterfoliage.MixinRenderChunkBlockTrackVanilla", "betterfoliage.MixinRenderChunkBlockTrackBetterFoliage",
            "MixinRenderGlobal", "celeritas.MixinRenderGlobal", "neonium.MixinBufferBuilder", "neonium.MixinChunkRenderManager",
            "neonium.MixinRenderGlobal", "neonium.MixinSodiumWorldRenderer", "nothirium.MixinBufferBuilder",
            "nothirium.MixinBufferBuilderOptifine", "nothirium.MixinAbstractChunkRenderer", "nothirium.MixinRenderGlobal",
            "optifine.MixinBufferBuilder", "optifine.MixinRenderGlobal");

    // MixinConfig is package private, so its instances travel as Object.
    private static final Class<?> MIXIN_CONFIG = mixinConfigType();

    private static Class<?> mixinConfigType() {
        try {
            return Class.forName("org.spongepowered.asm.mixin.transformer.MixinConfig");
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

    // Uninitialised MixinConfig, the state Gson leaves before Mixin prepares it; the real constructor needs a mixin service.
    private static Object config(List<String> client) {
        Object config = Statics.allocate(MIXIN_CONFIG);
        Mixins.set(config, "mixinClassesClient", client);
        return config;
    }

    @Test
    void onlyTheVanillaTerrainHookLeavesNtm2610sClientList() throws Exception {
        Object config = config(NTM_2610_CLIENT);
        assertTrue(HbmCompat.withdraw(config, HbmCompat.TERRAIN_HOOK));
        List<String> expected = new ArrayList<>(NTM_2610_CLIENT);
        expected.remove("MixinRenderGlobal");
        assertEquals(expected, Mixins.get(config, "mixinClassesClient"));
        // Same named mixins in subpackages stay, the immutable source list was replaced, not edited
        assertTrue(expected.contains("celeritas.MixinRenderGlobal"));
        assertEquals(15, NTM_2610_CLIENT.size());
        // Nothing left to withdraw on a second pass
        assertFalse(HbmCompat.withdraw(config, HbmCompat.TERRAIN_HOOK));
    }

    @Test
    void everyListIsCleanedIncludingRepeats() throws Exception {
        Object config = config(Arrays.asList("MixinRenderGlobal", "a.B", "MixinRenderGlobal"));
        Mixins.set(config, "mixinClasses", new ArrayList<>(List.of("MixinRenderGlobal")));
        Mixins.set(config, "mixinClassesServer", Collections.unmodifiableList(new ArrayList<>(List.of("c.D"))));
        assertTrue(HbmCompat.withdraw(config, HbmCompat.TERRAIN_HOOK));
        assertEquals(List.of("a.B"), Mixins.get(config, "mixinClassesClient"));
        assertEquals(List.of(), Mixins.get(config, "mixinClasses"));
        assertEquals(List.of("c.D"), Mixins.get(config, "mixinClassesServer"));
    }

    @Test
    void laterNtmReleasesWithoutTheHookAreLeftAlone() throws Exception {
        // NTM master dropped vanilla.MixinRenderGlobal after 2.6.1.0
        List<String> master = List.of("base.MixinBufferBuilder", "base.MixinRenderChunk", "optifine.MixinRenderChunk");
        Object config = config(master);
        assertFalse(HbmCompat.withdraw(config, HbmCompat.TERRAIN_HOOK));
        assertSame(master, Mixins.get(config, "mixinClassesClient"));
        // Missing lists, a missing config and a missing name are all nothing to do
        assertFalse(HbmCompat.withdraw(config(null), HbmCompat.TERRAIN_HOOK));
        assertFalse(HbmCompat.withdraw(null, HbmCompat.TERRAIN_HOOK));
        assertFalse(HbmCompat.withdraw(config(NTM_2610_CLIENT), null));
    }

    @Test
    void aPreparedConfigReportsTooLate() throws Exception {
        Object config = config(NTM_2610_CLIENT);
        Mixins.set(config, "prepared", true);
        assertFalse(HbmCompat.withdraw(config, HbmCompat.TERRAIN_HOOK));
    }

    @Test
    void theQueuedNtmConfigIsFoundByName(@TempDir Path home) throws Exception {
        // Config's static logger comes from the mixin service
        LaunchEnvironment.install(home);
        try {
            queuedConfig();
        } finally {
            LaunchEnvironment.restore();
        }
    }

    private static void queuedConfig() throws Exception {
        Map<String, Config> all = Statics.get(Config.class, "allConfigs");
        Object mixinConfig = config(NTM_2610_CLIENT);
        Config config = Statics.allocate(Config.class);
        Mixins.set(config, "config", mixinConfig);
        assertSame(mixinConfig, HbmCompat.mixinConfig(config));
        // Without NTM queued there is nothing to withdraw
        assertFalse(HbmCompat.withdrawTerrainHook());
        all.put(HbmCompat.VANILLA_CONFIG, config);
        try {
            assertTrue(HbmCompat.withdrawTerrainHook());
            assertFalse(((List<?>) Mixins.get(mixinConfig, "mixinClassesClient")).contains("MixinRenderGlobal"));
            assertFalse(HbmCompat.withdrawTerrainHook());
        } finally {
            all.remove(HbmCompat.VANILLA_CONFIG);
        }
        // An entry without its MixinConfig is nothing to do
        all.put(HbmCompat.VANILLA_CONFIG, Statics.allocate(Config.class));
        try {
            assertFalse(HbmCompat.withdrawTerrainHook());
        } finally {
            all.remove(HbmCompat.VANILLA_CONFIG);
        }
        assertNotNull(Mixins.construct(HbmCompat.class));
    }

    // hbm.vanilla.mixin.json exactly as NTM CE 2.6.1.0 ships it.
    private static final String NTM_2610_JSON = """
            {
              "required": true,
              "package": "com.hbm.mixin.vanilla",
              "plugin": "com.hbm.core.VanillaMixinConfigPlugin",
              "refmap": "mixins.hbm.refmap.json",
              "compatibilityLevel": "JAVA_8",
              "target": "@env(DEFAULT)",
              "client": [
                "base.MixinBufferBuilder",
                "betterfoliage.MixinRenderChunkBlockTrackVanilla",
                "betterfoliage.MixinRenderChunkBlockTrackBetterFoliage",
                "MixinRenderGlobal",
                "celeritas.MixinRenderGlobal",
                "neonium.MixinBufferBuilder",
                "neonium.MixinChunkRenderManager",
                "neonium.MixinRenderGlobal",
                "neonium.MixinSodiumWorldRenderer",
                "nothirium.MixinBufferBuilder",
                "nothirium.MixinBufferBuilderOptifine",
                "nothirium.MixinAbstractChunkRenderer",
                "nothirium.MixinRenderGlobal",
                "optifine.MixinBufferBuilder",
                "optifine.MixinRenderGlobal"
              ],
              "mixins": [],
              "server": [],
              "setSourceFile": true,
              "minVersion": "0.8.7"
            }
            """;

    @Test
    void theShippedJsonParsedTheWayMixinParsesItLosesOnlyTheHook(@TempDir Path home) throws Exception {
        // Mixin builds MixinConfig with Gson; its constructor asks the mixin service for a logger
        LaunchEnvironment.install(home);
        try {
            Object config = new Gson().fromJson(NTM_2610_JSON, MIXIN_CONFIG);
            assertEquals(NTM_2610_CLIENT, Mixins.get(config, "mixinClassesClient"));
            assertFalse((boolean) Mixins.<Boolean>get(config, "prepared"));
            assertTrue(HbmCompat.withdraw(config, HbmCompat.TERRAIN_HOOK));
            List<?> client = Mixins.get(config, "mixinClassesClient");
            assertEquals(14, client.size());
            assertFalse(client.contains("MixinRenderGlobal"));
            // The empty common and server lists are untouched
            assertEquals(List.of(), Mixins.get(config, "mixinClasses"));
            assertEquals(List.of(), Mixins.get(config, "mixinClassesServer"));
        } finally {
            LaunchEnvironment.restore();
        }
    }

    @Test
    void nullEntriesAndLookAlikeNamesSurvive() throws Exception {
        List<String> client = new ArrayList<>(Arrays.asList(null, "MixinRenderGlobal", "mixinrenderglobal",
                "MixinRenderGlobal ", "vanilla.MixinRenderGlobal", "MixinRenderGlobalExtra", null));
        Object config = config(client);
        assertTrue(HbmCompat.withdraw(config, HbmCompat.TERRAIN_HOOK));
        // Mixin matches names exactly, so only the exact name goes; nulls stay for Mixin to reject as it always did
        assertEquals(Arrays.asList(null, "mixinrenderglobal", "MixinRenderGlobal ", "vanilla.MixinRenderGlobal",
                "MixinRenderGlobalExtra", null), Mixins.get(config, "mixinClassesClient"));
        // The source list was replaced, never edited
        assertEquals(7, client.size());
    }

    @Test
    void somethingThatIsNotAMixinConfigThrowsForTheCallerToLog() {
        assertThrows(NoSuchFieldException.class, () -> HbmCompat.withdraw("not a config", HbmCompat.TERRAIN_HOOK));
        assertThrows(NoSuchFieldException.class, () -> HbmCompat.withdraw(new Object(), HbmCompat.TERRAIN_HOOK));
    }

    @Test
    void aListThatOnlyHoldsOtherTypesIsLeftAlone() throws Exception {
        Object config = config(null);
        List<Object> odd = new ArrayList<>(List.of(1, 2.0, 'c'));
        Mixins.set(config, "mixinClassesClient", odd);
        assertFalse(HbmCompat.withdraw(config, HbmCompat.TERRAIN_HOOK));
        assertSame(odd, Mixins.get(config, "mixinClassesClient"));
    }

    @Test
    void impetusPluginLoadWithdrawsTheHookEveryTime() {
        try (MockedStatic<HbmCompat> hbm = Mockito.mockStatic(HbmCompat.class)) {
            ImpetusVintageMixinPlugin plugin = new ImpetusVintageMixinPlugin();
            plugin.onLoad("com.bdmajora.impetus.mixin");
            plugin.onLoad("com.bdmajora.impetus.mixin");
            hbm.verify(HbmCompat::withdrawTerrainHook, Mockito.times(2));
        }
    }

    // NTM CE 2.6.1.0's hbm.common.mixin.json client list: it has its own MixinRenderGlobal that Impetus must keep.
    private static final List<String> NTM_2610_COMMON = List.of("MixinCompiledChunk", "MixinSetVisibility", "MixinRenderChunk",
            "MixinRenderGlobal", "MixinTileEntity", "MixinRenderPlayerManly");

    // Runs the body with the given configs queued under their names, then takes them out again.
    private static void withQueued(Path home, Map<String, Object> mixinConfigs, ThrowingRunnable body) throws Exception {
        LaunchEnvironment.install(home);
        Map<String, Config> all = Statics.get(Config.class, "allConfigs");
        try {
            for (Map.Entry<String, Object> entry : mixinConfigs.entrySet()) {
                Config config = Statics.allocate(Config.class);
                Mixins.set(config, "config", entry.getValue());
                all.put(entry.getKey(), config);
            }
            body.run();
        } finally {
            mixinConfigs.keySet().forEach(all::remove);
            LaunchEnvironment.restore();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Test
    void ntmsCommonConfigKeepsItsOwnMixinRenderGlobal(@TempDir Path home) throws Exception {
        // hbm.common's MixinRenderGlobal hooks renderEntities, which Impetus leaves in place; only the vanilla config's goes
        Object vanilla = config(NTM_2610_CLIENT);
        Object common = config(NTM_2610_COMMON);
        withQueued(home, Map.of(HbmCompat.VANILLA_CONFIG, vanilla, "hbm.common.mixin.json", common, "hbm.mod.mixin.json", config(List.of("MixinRenderGlobal"))), () -> {
            assertTrue(HbmCompat.withdrawTerrainHook());
            assertSame(NTM_2610_COMMON, Mixins.get(common, "mixinClassesClient"));
            assertFalse(((List<?>) Mixins.get(vanilla, "mixinClassesClient")).contains("MixinRenderGlobal"));
        });
    }

    @Test
    void onlyTheExactConfigNameIsLookedUp(@TempDir Path home) throws Exception {
        // A renamed or repackaged config is not NTM's, whatever it lists
        Object lookAlike = config(NTM_2610_CLIENT);
        withQueued(home, Map.of("HBM.VANILLA.MIXIN.JSON", lookAlike, "mixins/hbm.vanilla.mixin.json", config(NTM_2610_CLIENT),
                "hbm.vanilla.mixin.json.bak", config(NTM_2610_CLIENT)), () -> {
            assertFalse(HbmCompat.withdrawTerrainHook());
            assertSame(NTM_2610_CLIENT, Mixins.get(lookAlike, "mixinClassesClient"));
        });
    }

    @Test
    void aMixinServiceFailureIsLoggedNotThrown(@TempDir Path home) throws Exception {
        LaunchEnvironment.install(home);
        try (MockedStatic<Config> configs = Mockito.mockStatic(Config.class)) {
            // Covers RuntimeException and LinkageError out of the config registry
            configs.when(Config::getAllConfigs).thenThrow(new IllegalStateException("no service"));
            assertFalse(HbmCompat.withdrawTerrainHook());
            configs.when(Config::getAllConfigs).thenThrow(new NoClassDefFoundError("org/spongepowered/asm/mixin/transformer/Config"));
            assertFalse(HbmCompat.withdrawTerrainHook());
            configs.when(Config::getAllConfigs).thenReturn(null);
            assertFalse(HbmCompat.withdrawTerrainHook());
        } finally {
            LaunchEnvironment.restore();
        }
    }

    @Test
    void aTooLateConfigStillLosesTheNameButReportsFalse() throws Exception {
        // Prepared means MixinInfo was already built; the name list edit is harmless, and the false makes the log say so
        Object config = config(NTM_2610_CLIENT);
        Mixins.set(config, "prepared", true);
        assertFalse(HbmCompat.withdraw(config, HbmCompat.TERRAIN_HOOK));
        assertFalse(((List<?>) Mixins.get(config, "mixinClassesClient")).contains("MixinRenderGlobal"));
        // Nothing listed and prepared is plain "nothing to do", without the too-late warning path
        Object empty = config(List.of("base.MixinBufferBuilder"));
        Mixins.set(empty, "prepared", true);
        assertFalse(HbmCompat.withdraw(empty, HbmCompat.TERRAIN_HOOK));
    }

    @Test
    void theHookListedInEveryListAtOnceLeavesEachEmpty() throws Exception {
        Object config = config(List.of("MixinRenderGlobal"));
        Mixins.set(config, "mixinClasses", List.of("MixinRenderGlobal", "MixinRenderGlobal"));
        Mixins.set(config, "mixinClassesServer", List.of("MixinRenderGlobal"));
        assertTrue(HbmCompat.withdraw(config, HbmCompat.TERRAIN_HOOK));
        for (String list : new String[] {"mixinClasses", "mixinClassesClient", "mixinClassesServer"}) {
            List<?> left = Mixins.get(config, list);
            assertTrue(left.isEmpty(), list);
            // Still a list Mixin can iterate and Gson-style callers can extend
            assertDoesNotThrow(() -> ((List<Object>) left).add("x"), list);
        }
    }

    @Test
    void aLongListWithTheHookLastKeepsItsOrder() throws Exception {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            many.add("pkg" + i + ".MixinThing");
        }
        many.add("MixinRenderGlobal");
        Object config = config(many);
        assertTrue(HbmCompat.withdraw(config, HbmCompat.TERRAIN_HOOK));
        assertEquals(many.subList(0, 500), Mixins.get(config, "mixinClassesClient"));
    }
}
