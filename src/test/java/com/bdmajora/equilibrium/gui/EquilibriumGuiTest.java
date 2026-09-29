package com.bdmajora.equilibrium.gui;

import com.bdmajora.equilibrium.Equilibrium;
import com.bdmajora.equilibrium.config.EquilibriumConfig;
import com.bdmajora.equilibrium.config.EquilibriumOptions;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpact;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.testing.Mixins;
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
    void pagesMirrorTheOptionTreeAndWriteThroughToTheConfig() throws Exception {
        Path file = dir.resolve("gui.properties");
        Equilibrium.setConfig(EquilibriumConfig.load(file));
        List<OptionPage> pages = EquilibriumOptionPages.pages();
        assertEquals(3, pages.size());
        assertEquals("page.world", pages.get(0).getId().getPath());
        assertEquals("page.entities", pages.get(1).getId().getPath());
        assertEquals("page.internals", pages.get(2).getId().getPath());
        int toggles = 0;
        Option<?> raycast = null;
        for (OptionPage page : pages) {
            for (OptionGroup group : page.getGroups()) {
                assertTrue(group.getId().getPath().startsWith("group."));
                for (Option<?> option : group.getOptions()) {
                    toggles++;
                    assertTrue(option.getFlags().contains(OptionFlag.REQUIRES_GAME_RESTART));
                    if (option.getId().getPath().equals("world_raycast")) {
                        raycast = option;
                    }
                    String category = EquilibriumOptions.get("mixin." + option.getId().getPath().replace('_', '.')) == null
                            ? null : EquilibriumOptions.get("mixin." + option.getId().getPath().replace('_', '.')).category();
                    if (category != null) {
                        OptionImpact expected = switch (category) {
                            case "world" -> OptionImpact.HIGH;
                            case "entity", "math", "worldgen", "advancements" -> OptionImpact.MEDIUM;
                            case "ai", "alloc", "block", "chunk" -> OptionImpact.LOW;
                            default -> null;
                        };
                        assertEquals(expected, option.getImpact(), option.getId().getPath());
                    }
                }
            }
        }
        assertEquals(EquilibriumOptions.entries().size(), toggles);
        assertNotNull(raycast);
        @SuppressWarnings("unchecked")
        Option<Boolean> toggle = (Option<Boolean>) raycast;
        assertTrue(toggle.getValue());
        toggle.setValue(false);
        assertTrue(toggle.hasChanged());
        toggle.applyChanges();
        assertFalse(Equilibrium.config().isOptionEnabled("mixin.world.raycast"));
        toggle.getStorage().save();
        assertTrue(Files.readString(file).contains("\nmixin.world.raycast=false\n"));
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
