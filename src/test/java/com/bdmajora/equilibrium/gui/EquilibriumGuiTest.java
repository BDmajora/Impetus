package com.bdmajora.equilibrium.gui;

import com.bdmajora.equilibrium.Equilibrium;
import com.bdmajora.equilibrium.config.EquilibriumConfig;
import com.bdmajora.equilibrium.config.EquilibriumOptions;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.OptionPages;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.text.ITextComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EquilibriumGuiTest {
    @TempDir
    Path dir;

    @AfterEach
    void forgetSharedConfig() {
        Mixins.set(Equilibrium.class, "config", null);
    }

    @Test
    void theMasterSwitchStopsEveryPatchAndKeepsTheKeysBelowIt() throws Exception {
        Path file = dir.resolve("gui.properties");
        Equilibrium.setConfig(EquilibriumConfig.load(file));
        EquilibriumConfig config = Equilibrium.config();
        Option<Boolean> logic = EquilibriumOptionPages.gameLogic();
        OptionPages.exercise(logic);
        assertTrue(logic.getFlags().contains(OptionFlag.REQUIRES_GAME_RESTART));
        assertTrue(logic.getValue());

        // Off stops every patch and leaves every key below as it was.
        config.setOptionEnabled("mixin.world.raycast", false);
        logic.setValue(false);
        assertTrue(logic.hasChanged());
        logic.applyChanges();
        assertFalse(config.isEnabled());
        for (String category : EquilibriumOptions.categories()) {
            assertFalse(config.isOptionEnabled("mixin." + category), category);
        }
        assertEquals("enabled", config.getEffectiveOptionForMixin("world.raycast.SomeMixin").getName());
        assertFalse(config.getEffectiveOptionForMixin("ai.goal_selector.SomeMixin").isEnabled());
        assertTrue(config.getOption("mixin.world").isEnabled());
        logic.getStorage().save();
        String text = Files.readString(file);
        assertTrue(text.contains("\nenabled=false\n"));
        assertFalse(text.contains("\nmixin.world=false\n"));
        assertTrue(text.contains("\nmixin.world.raycast=false\n"));

        // The next launch reads the master key back.
        EquilibriumConfig reloaded = EquilibriumConfig.load(file);
        assertFalse(reloaded.isEnabled());
        assertFalse(reloaded.isOptionEnabled("mixin.world"));

        logic.setValue(true);
        logic.applyChanges();
        assertTrue(config.isOptionEnabled("mixin.world"));
        assertTrue(config.isOptionEnabled("mixin.ai.goal_selector"));
        assertFalse(config.isOptionEnabled("mixin.world.raycast"));
        assertTrue(config.getEffectiveOptionForMixin("ai.goal_selector.SomeMixin").isEnabled());
        assertNotNull(Mixins.construct(EquilibriumOptionPages.class));
    }

    @Test
    void statsCommandPrintsTheReportInTwoColours() {
        Equilibrium.setConfig(EquilibriumConfig.load(dir.resolve("cmd.properties")));
        EquilibriumStatsCommand command = new EquilibriumStatsCommand();
        assertEquals("equilibrium", command.getName());
        assertTrue(command.getUsage(null).startsWith("/equilibrium"));
        assertEquals(0, command.getRequiredPermissionLevel());
        ICommandSender sender = Mockito.mock(ICommandSender.class);
        command.execute(null, sender, new String[0]);
        ArgumentCaptor<ITextComponent> messages = ArgumentCaptor.forClass(ITextComponent.class);
        Mockito.verify(sender, Mockito.atLeast(2)).sendMessage(messages.capture());
        List<ITextComponent> sent = messages.getAllValues();
        assertTrue(sent.get(0).getUnformattedText().startsWith("§bEquilibrium: "));
        assertTrue(sent.stream().anyMatch(m -> m.getUnformattedText().startsWith("§7  ")));
    }
}
