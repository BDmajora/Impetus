package com.bdmajora.coarctatio.gui;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.OptionPages;
import com.bdmajora.testing.Statics;
import net.minecraft.command.ICommandSender;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.text.ITextComponent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CoarctatioGuiTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.forge();
    }

    @BeforeEach
    void freshConfig() {
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(CoarctatioConfig.class, "instance", null);
    }

    @Test
    void theMemorySwitchKeepsEveryFeatureSettingInTheFile() throws Exception {
        Option<Boolean> memory = CoarctatioOptionPages.memory();
        OptionPages.exercise(memory);
        assertTrue(memory.getFlags().contains(OptionFlag.REQUIRES_GAME_RESTART));
        CoarctatioConfig config = CoarctatioConfig.get();
        assertSame(config, memory.getStorage().getData());
        assertTrue(memory.getValue());
        Path file = dir.resolve("config/impetus-coarctatio.cfg");

        // Off writes only the master key and leaves the feature keys as they were.
        config.dynamicModels = true;
        memory.setValue(false);
        assertTrue(memory.hasChanged());
        memory.applyChanges();
        assertFalse(config.enabled);
        assertTrue(config.optimizeBlockStates);
        memory.getStorage().save();
        String text = Files.readString(file);
        assertTrue(text.contains("\nenabled=false\n"));
        assertTrue(text.contains("\noptimizeBlockStates=true\n"));
        assertTrue(text.contains("\ndynamicModels=true\n"));

        // The next launch runs no feature but keeps the file values.
        Mixins.set(CoarctatioConfig.class, "instance", null);
        CoarctatioConfig held = CoarctatioConfig.get();
        assertFalse(held.enabled);
        assertFalse(held.optimizeBlockStates);
        assertFalse(held.dynamicModels);
        assertTrue(held.showDebugOverlay);
        assertTrue(Files.readString(file).contains("\ndynamicModels=true\n"));

        // On again restores the file values on the launch after.
        Option<Boolean> again = CoarctatioOptionPages.memory();
        assertFalse(again.getValue());
        again.setValue(true);
        again.applyChanges();
        again.getStorage().save();
        Mixins.set(CoarctatioConfig.class, "instance", null);
        CoarctatioConfig restored = CoarctatioConfig.get();
        assertTrue(restored.enabled);
        assertTrue(restored.optimizeBlockStates);
        assertTrue(restored.dynamicModels);
        assertFalse(restored.recycleEvents);
        assertNotNull(Mixins.construct(CoarctatioOptionPages.class));
    }

    @Test
    void theFileNotesAndDefaultsEveryKey() throws Exception {
        CoarctatioConfig.get();
        String text = Files.readString(dir.resolve("config/impetus-coarctatio.cfg"));
        assertTrue(text.startsWith("# Impetus memory features."));
        assertTrue(text.contains("# Turns every memory feature on."));
        assertTrue(text.contains("# Default: true\ndynamicModels=true\n"));
        assertTrue(text.contains("# Default: jds.bibliocraft\nblockStateBlacklist=jds.bibliocraft\n"));
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isEmpty() && !lines[i].startsWith("#")) {
                assertTrue(lines[i - 1].startsWith("# Default: "), lines[i]);
                assertTrue(lines[i - 2].startsWith("# ") && !lines[i - 2].startsWith("# Default: "), lines[i]);
            }
        }
    }

    @Test
    void theCommandPrintsTheReportAndTheCounters() {
        CoarctatioStatsCommand command = new CoarctatioStatsCommand();
        assertEquals("coarctatio", command.getName());
        assertTrue(command.getUsage(mock(ICommandSender.class)).startsWith("/coarctatio"));
        // Checks the permission level.
        assertEquals(0, command.getRequiredPermissionLevel());

        ICommandSender sender = mock(ICommandSender.class);
        command.execute(null, sender, new String[0]);
        Mockito.verify(sender, Mockito.atLeastOnce()).sendMessage(Mockito.any(ITextComponent.class));
    }
}
