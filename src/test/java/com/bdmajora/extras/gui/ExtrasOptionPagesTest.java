package com.bdmajora.extras.gui;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.OptionPages;
import com.bdmajora.testing.Statics;
import net.minecraft.client.Minecraft;
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
    }

    @Test
    void everyOptionOnBothPagesReadsAndWritesTheLiveConfig() throws Exception {
        OptionPage details = ExtrasOptionPages.detailsPage();
        OptionPage interfacePage = ExtrasOptionPages.interfacePage();
        assertEquals("impetus:details", details.getId().toString());
        assertEquals("impetus:interface", interfacePage.getId().toString());
        assertEquals(4, details.getGroups().size());
        assertEquals(4, interfacePage.getGroups().size());
        assertEquals(36, OptionPages.exercise(List.of(details, interfacePage)));
        // The master switches the Performance page shows
        List<Option<?>> switches = List.of(ExtrasOptionPages.verticalSync(), ExtrasOptionPages.occlusionCulling(),
                ExtrasOptionPages.leafCulling(), ExtrasOptionPages.renderBudget(), ExtrasOptionPages.gpuBooster(),
                ExtrasOptionPages.parallelTicking(), ExtrasOptionPages.bakedBlockEntities());
        switches.forEach(OptionPages::exercise);
        assertTrue(ExtrasOptionPages.parallelTicking().getFlags().contains(OptionFlag.REQUIRES_GAME_RESTART));

        // Checks the render budget row cycles Off first and keeps the profile while off.
        assertEquals("[OFF, PERFORMANCE, BALANCED, QUALITY]", java.util.Arrays.toString(ExtrasConfig.RenderBudgetMode.values()));
        ExtrasConfig.RenderBudgetSettings budget = Extras.options().renderBudget;
        Option<ExtrasConfig.RenderBudgetMode> mode = ExtrasOptionPages.renderBudget();
        assertEquals("extras:extras/budget.mode", mode.getId().toString());
        budget.enabled = false;
        assertEquals(ExtrasConfig.RenderBudgetMode.OFF, mode.getValue());
        mode.setValue(ExtrasConfig.RenderBudgetMode.QUALITY);
        mode.applyChanges();
        assertTrue(budget.enabled);
        assertEquals(ExtrasConfig.BudgetProfile.QUALITY, budget.profile);
        mode.setValue(ExtrasConfig.RenderBudgetMode.OFF);
        mode.applyChanges();
        assertFalse(budget.enabled);
        assertEquals(ExtrasConfig.BudgetProfile.QUALITY, budget.profile);
        for (ExtrasConfig.RenderBudgetMode each : ExtrasConfig.RenderBudgetMode.values()) {
            each.applyTo(budget);
            assertEquals(each, ExtrasConfig.RenderBudgetMode.of(budget));
        }
        assertEquals("options.off", ExtrasConfig.RenderBudgetMode.OFF.translationKey());
        budget.enabled = false;
        budget.profile = ExtrasConfig.BudgetProfile.BALANCED;

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
    void theParticleSwitchesFollowTheMasterSwitch() {
        OptionPage details = ExtrasOptionPages.detailsPage();
        @SuppressWarnings("unchecked")
        Option<Boolean> particles = (Option<Boolean>) details.getGroups().get(0).getOptions().get(1);
        Option<?> rain = details.getGroups().get(0).getOptions().get(2);
        assertTrue(rain.isAvailable());
        particles.setValue(false);
        assertFalse(rain.isAvailable());
        particles.applyChanges();
        assertFalse(Extras.options().particle.all);
    }

    @Test
    void theZoomSettingsFollowTheZoomSwitch() {
        OptionPage interfacePage = ExtrasOptionPages.interfacePage();
        @SuppressWarnings("unchecked")
        Option<Boolean> zoom = (Option<Boolean>) interfacePage.getGroups().get(3).getOptions().get(0);
        Option<?> level = interfacePage.getGroups().get(3).getOptions().get(1);
        assertTrue(level.isAvailable());
        zoom.setValue(false);
        assertFalse(level.isAvailable());
        // The panini switch does not depend on zoom
        assertTrue(interfacePage.getGroups().get(3).getOptions().get(4).isAvailable());
    }
}
