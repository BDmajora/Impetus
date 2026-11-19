package com.bdmajora.impetus.core;

import com.bdmajora.linuxextras.LinuxExtras;
import com.bdmajora.testing.LaunchEnvironment;
import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.Config;
import zone.rong.mixinbooter.service.ModDiscoverer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CoreTest {
    private static final List<String> IMPETUS_CONFIGS = List.of("mixins.impetus.json", "mixins.umbra.json", "mixins.coarctatio.json",
            "mixins.fulgor.json", "mixins.equilibrium.json", "mixins.extras.json", "mixins.dynamiclights.json",
            "mixins.linuxextras.json");

    @Test
    void theRegistrarQueuesEveryImpetusConfig(@TempDir Path home) {
        LaunchEnvironment.install(home);
        try (MockedStatic<Mixins> mixins = Mockito.mockStatic(Mixins.class)) {
            ImpetusMixinRegistrar.queueImpetusConfigs();
            for (String config : IMPETUS_CONFIGS) {
                mixins.verify(() -> Mixins.addConfiguration(config));
            }
        } finally {
            LaunchEnvironment.restore();
        }
    }

    @Test
    void theRegistrarSuppressesOnlyInstalledLightingEngines(@TempDir Path home) {
        LaunchEnvironment.install(home);
        try (MockedStatic<ModDiscoverer> mods = Mockito.mockStatic(ModDiscoverer.class);
             MockedStatic<Config> configs = Mockito.mockStatic(Config.class)) {
            // Reports Phosphor as installed.
            mods.when(() -> ModDiscoverer.isModPresent("phosphor-lighting")).thenReturn(true);
            ImpetusMixinRegistrar.hijackSupersededLighting();
            configs.verify(() -> Config.blacklist("mixins.phosphor.json"));
            configs.verify(() -> Config.blacklist("mixins.alfheim.json"), never());
        } finally {
            LaunchEnvironment.restore();
        }
    }

    @Test
    void theLoadingPluginOnlyRegistersItsOwnMixins() {
        try (MockedStatic<ImpetusMixinRegistrar> registrar = Mockito.mockStatic(ImpetusMixinRegistrar.class);
             MockedStatic<TransformerChain> chain = Mockito.mockStatic(TransformerChain.class);
             MockedStatic<LinuxExtras> linux = Mockito.mockStatic(LinuxExtras.class)) {
            ImpetusLoadingPlugin plugin = new ImpetusLoadingPlugin();
            chain.verify(TransformerChain::makeConcurrent);
            registrar.verify(ImpetusMixinRegistrar::hijackSupersededLighting);
            registrar.verify(ImpetusMixinRegistrar::queueImpetusConfigs);
            linux.verifyNoInteractions();
            plugin.injectData(Map.of());
            registrar.verifyNoMoreInteractions();

            assertEquals(0, plugin.getASMTransformerClass().length);
            assertNull(plugin.getModContainerClass());
            assertNull(plugin.getSetupClass());
            assertNull(plugin.getAccessTransformerClass());
        }
    }

    @Test
    void theSharedPropertiesFileParsesLeniently(@TempDir Path home) throws IOException {
        LaunchEnvironment.install(home);
        try {
            Logger logger = mock(Logger.class);
            PropertiesConfig config = new PropertiesConfig(logger, "switches.cfg", "Switches");
            // Nothing to write before a load has resolved the file
            config.save(Map.of("a", "b"));
            Properties defaults = config.load();
            assertTrue(defaults.isEmpty());
            Map<String, String> values = PropertiesConfig.values();
            values.put("flag", "true");
            values.put("fuzzy", "maybe");
            values.put("count", "5");
            values.put("huge", "99");
            values.put("word", "five");
            values.put("items", " a, ,b ,");
            config.save(values);
            assertEquals(home.resolve("config/switches.cfg").toFile(), PropertiesConfig.configFile(logger, "switches.cfg"));
            config.load();
            assertTrue(config.bool("flag", false));
            assertTrue(config.bool("fuzzy", true));
            assertFalse(config.bool("absent", false));
            assertEquals(5, config.integer("count", 1, 0, 10));
            assertEquals(1, config.integer("huge", 1, 0, 10));
            assertEquals(1, config.integer("word", 1, 0, 10));
            assertEquals(1, config.integer("absent", 1, 0, 10));
            assertArrayEquals(new String[] {"a", "b"}, config.list("items", ""));
            assertArrayEquals(new String[] {"x", "y"}, config.list("absent", "x,y"));

            // Checks an unreadable file and a bad config directory.
            Files.createDirectories(home.resolve("config/unreadable.cfg"));
            assertTrue(new PropertiesConfig(logger, "unreadable.cfg", "").load().isEmpty());
            Path blocked = home.resolve("blocked");
            Files.createDirectories(blocked);
            Files.writeString(blocked.resolve("config"), "a file where the directory belongs");
            Launch.minecraftHome = blocked.toFile();
            PropertiesConfig stranded = new PropertiesConfig(logger, "switches.cfg", "");
            stranded.load();
            stranded.save(values);
            verify(logger, Mockito.atLeastOnce()).warn(Mockito.anyString(), Mockito.any(Object.class));
            verify(logger).warn(Mockito.eq("Could not write {}"), Mockito.any(Object.class), Mockito.any(IOException.class));
        } finally {
            LaunchEnvironment.restore();
        }
    }

    @Test
    void theSharedPropertiesFileKeepsOrderNotesAndDefaultsAndReadsBackExactly(@TempDir Path home) throws IOException {
        LaunchEnvironment.install(home);
        try {
            PropertiesConfig config = new PropertiesConfig(mock(Logger.class), "notes.cfg", "Header line.\nSecond paragraph.");
            config.load();
            Map<String, String> values = new LinkedHashMap<>();
            values.put("zeta", "true");
            values.put("alpha", "1");
            values.put("odd key=:#!", " lead\ttab\nline\\back = : # !");
            values.put("empty", "");
            values.put("wide", "caf\u00e9 \u20ac \uD83D\uDE00");
            String note = "word ".repeat(40).trim();
            config.save(values, Map.of("zeta", note, "wide", "Euro \u20ac sign"), Map.of("zeta", "false", "alpha", "1"));
            String text = Files.readString(home.resolve("config/notes.cfg"), StandardCharsets.ISO_8859_1);

            // Checks the header and then each key under its note and default in order.
            assertTrue(text.startsWith("# Header line.\n# Second paragraph.\n\n# word "));
            assertTrue(text.indexOf("\nzeta=") < text.indexOf("\nalpha=") && text.indexOf("\nalpha=") < text.indexOf("\nodd"));
            assertTrue(text.contains("# Default: false\nzeta=true\n"));
            assertTrue(text.contains("\n\n# Default: 1\nalpha=1\n"));
            assertTrue(text.contains("\n\nempty=\n"));
            assertTrue(text.contains("# Euro \\u20AC sign\n"));
            for (String line : text.split("\n")) {
                assertTrue(line.length() <= 78, line);
                assertTrue(line.chars().allMatch(c -> c >= 0x20 && c < 0x7F), line);
            }
            assertEquals(3, text.lines().filter(line -> line.startsWith("# word")).count());

            // Properties reads every key and value back unchanged.
            Properties read = new Properties();
            try (var in = Files.newInputStream(home.resolve("config/notes.cfg"))) {
                read.load(in);
            }
            assertEquals(values, new LinkedHashMap<>(Map.copyOf((Map) read)));

            // The one argument save writes the header and keys alone.
            config.save(Map.of("only", "x"));
            assertEquals("# Header line.\n# Second paragraph.\n\nonly=x\n", Files.readString(home.resolve("config/notes.cfg")));
            assertEquals("only=x\n", new PropertiesConfig(mock(Logger.class), "bare.cfg", "").format(Map.of("only", "x"), Map.of(), Map.of()));
        } finally {
            LaunchEnvironment.restore();
        }
    }

    @Test
    void theSharedMixinPluginAppliesEverything() {
        SimpleMixinPlugin plugin = new SimpleMixinPlugin() {
        };
        plugin.onLoad("com.example.mixin");
        assertNull(plugin.getRefMapperConfig());
        assertTrue(plugin.shouldApplyMixin("a.Target", "a.Mixin"));
        plugin.acceptTargets(Set.of(), Set.of());
        assertNull(plugin.getMixins());
        plugin.preApply("a.Target", new ClassNode(), "a.Mixin", null);
        plugin.postApply("a.Target", new ClassNode(), "a.Mixin", null);
    }
}
