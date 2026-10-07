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
            // Cleanroom's discoverer knows Phosphor is installed, not Alfheim
            mods.when(() -> ModDiscoverer.isModPresent("phosphor-lighting")).thenReturn(true);
            ImpetusMixinRegistrar.hijackSupersededLighting();
            configs.verify(() -> Config.blacklist("mixins.phosphor.json"));
            configs.verify(() -> Config.blacklist("mixins.alfheim.json"), never());
        } finally {
            LaunchEnvironment.restore();
        }
    }

    @Test
    void theRegistrarRestoresDistantHorizonsDepthTextureOnlyWhenPresent(@TempDir Path home, @TempDir Path classpath) throws IOException {
        // Without Distant Horizons on the classpath its depth-texture config is left alone
        LaunchEnvironment.install(home, classpath.toUri().toURL());
        try (MockedStatic<Mixins> mixins = Mockito.mockStatic(Mixins.class)) {
            ImpetusMixinRegistrar.restoreDistantHorizonsDepthTexture();
            mixins.verify(() -> Mixins.addConfiguration("DistantHorizons.iris.mixins.json"), never());
        } finally {
            LaunchEnvironment.restore();
        }
        // DH skipped it on seeing Impetus's IrisApi, so Impetus queues it
        Files.writeString(classpath.resolve("DistantHorizons.iris.mixins.json"), "{}");
        LaunchEnvironment.install(home, classpath.toUri().toURL());
        try (MockedStatic<Mixins> mixins = Mockito.mockStatic(Mixins.class)) {
            ImpetusMixinRegistrar.restoreDistantHorizonsDepthTexture();
            mixins.verify(() -> Mixins.addConfiguration("DistantHorizons.iris.mixins.json"));
        } finally {
            LaunchEnvironment.restore();
        }
    }

    @Test
    void theLoadingPluginRegistersFromItsConstructorAndDhFromInjectData() {
        try (MockedStatic<ImpetusMixinRegistrar> registrar = Mockito.mockStatic(ImpetusMixinRegistrar.class);
             MockedStatic<LinuxExtras> linux = Mockito.mockStatic(LinuxExtras.class)) {
            ImpetusLoadingPlugin plugin = new ImpetusLoadingPlugin();
            registrar.verify(ImpetusMixinRegistrar::hijackSupersededLighting);
            registrar.verify(ImpetusMixinRegistrar::queueImpetusConfigs);
            // The window system is settled before anything can start GLFW
            linux.verify(LinuxExtras::chooseWindowSystem);
            registrar.verify(ImpetusMixinRegistrar::restoreDistantHorizonsDepthTexture, never());
            plugin.injectData(Map.of());
            registrar.verify(ImpetusMixinRegistrar::restoreDistantHorizonsDepthTexture);

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

            // An unreadable file falls back to defaults, and a config directory that cannot exist is reported
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
