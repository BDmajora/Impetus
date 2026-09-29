package com.bdmajora.extras.gui;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.extras.client.particle.ParticleClassRegistry;
import com.bdmajora.extras.mixin.particle.ParticleManagerAccessor;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.OptionPages;
import com.bdmajora.testing.Statics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExtrasOptionPagesTest {
    @TempDir
    Path dir;

    private Minecraft client;

    // A particle class from outside net.minecraft, listed under its own mod's heading
    static class ModdedParticle {
    }

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @BeforeEach
    void freshConfig() {
        client = Mc.client();
        Mixins.set(client, "gameSettings", mock(GameSettings.class));
        Statics.set(FMLInjectionData.class, "minecraftHome", dir.toFile());
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        // The thread slider's automatic count depends on which side is running
        Statics.set(net.minecraftforge.fml.relauncher.FMLLaunchHandler.class, "side", net.minecraftforge.fml.relauncher.Side.CLIENT);
        Mixins.set(Extras.class, "config", null);
    }

    @AfterEach
    void forgetConfig() {
        Mixins.set(Extras.class, "config", null);
        Mixins.set(client, "effectRenderer", null);
    }

    @Test
    void everyOptionOnEveryPageReadsAndWritesTheLiveConfig() throws Exception {
        // Discovered particle classes get a toggle each, grouped by mod
        ParticleClassRegistry.getInstance().loadDiscoveredClasses(new String[] {
                "net.minecraft.client.particle.ParticleFlame|ParticleFlame",
                ModdedParticle.class.getName() + "|ModdedParticle"});
        Mixins.set(client, "effectRenderer", Mc.mock(ParticleManager.class, ParticleManagerAccessor.class));

        List<OptionPage> pages = ExtrasOptionPages.pages();
        assertEquals(9, pages.size());
        assertTrue(OptionPages.exercise(pages) > 100);

        // The screen edits the live config and saves it when dismissed
        ExtrasOptionsStorage storage = new ExtrasOptionsStorage();
        assertSame(Extras.options(), storage.getData());
        storage.save();
        assertTrue(Files.isRegularFile(dir.resolve("config/impetus-extras.cfg")));
        // The vanilla-bound switches write through to the game's own settings
        verify(client.gameSettings, org.mockito.Mockito.atLeastOnce()).saveOptions();
        assertEquals(ExtrasConfig.VerticalSync.OFF, com.bdmajora.extras.client.AdaptiveSync.current());
        assertNotNull(Mixins.construct(ExtrasOptionPages.class));
    }

    @Test
    void withNoParticleClassesSeenTheirPageIsEmpty() {
        Mixins.<java.util.Map<?, ?>>get(ParticleClassRegistry.getInstance(), "discoveredClasses").clear();
        List<OptionPage> pages = ExtrasOptionPages.pages();
        assertTrue(pages.get(2).getGroups().isEmpty());
    }
}
