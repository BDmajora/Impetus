package com.bdmajora.coarctatio.gui;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
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
import java.util.ArrayList;
import java.util.List;

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

    // Reads an option and writes the same value back, so both halves of its binding run
    private static <T> void touch(Option<T> option) {
        T value = option.getValue();
        option.setValue(value);
        option.applyChanges();
    }

    @Test
    void theTwoPagesCoverEverySwitchInTheConfig() throws Exception {
        List<OptionPage> pages = CoarctatioOptionPages.pages();
        assertEquals(2, pages.size());
        assertEquals("page.loading", pages.get(0).getId().getPath());
        assertEquals("page.data", pages.get(1).getId().getPath());

        List<String> ids = new ArrayList<>();
        Option<Boolean> restartFlagged = null;
        Option<Boolean> live = null;
        for (OptionPage page : pages) {
            assertFalse(page.getGroups().isEmpty());
            for (OptionGroup group : page.getGroups()) {
                for (Option<?> option : group.getOptions()) {
                    ids.add(option.getId().getPath());
                    touch(option);
                    if (option.getId().getPath().equals("optimize_block_states")) {
                        restartFlagged = castToggle(option);
                    }
                    if (option.getId().getPath().equals("show_debug_overlay")) {
                        live = castToggle(option);
                    }
                }
            }
        }
        // Every config switch the screen exposes, plus the one numeric slider
        assertTrue(ids.size() >= 30, "only " + ids.size() + " options");
        assertTrue(ids.contains("nbt_array_map_threshold"));
        assertNotNull(restartFlagged);
        assertNotNull(live);

        // The mixin plugin reads the config before the window exists, so nearly everything needs a restart
        assertTrue(restartFlagged.getFlags().contains(OptionFlag.REQUIRES_GAME_RESTART));
        // The F3 line is read every frame, so it applies immediately
        assertFalse(live.getFlags().contains(OptionFlag.REQUIRES_GAME_RESTART));

        // Writing goes through the storage to the live config and then to the file
        restartFlagged.setValue(false);
        assertTrue(restartFlagged.hasChanged());
        restartFlagged.applyChanges();
        assertFalse(CoarctatioConfig.get().optimizeBlockStates);
        assertSame(CoarctatioConfig.get(), restartFlagged.getStorage().getData());
        restartFlagged.getStorage().save();
        assertTrue(Files.readString(dir.resolve("config/impetus-coarctatio.cfg")).contains("optimizeBlockStates=false"));
        assertNotNull(Mixins.construct(CoarctatioOptionPages.class));
    }

    @SuppressWarnings("unchecked")
    private static Option<Boolean> castToggle(Option<?> option) {
        return (Option<Boolean>) option;
    }

    @Test
    void theCommandPrintsTheReportAndTheCounters() {
        CoarctatioStatsCommand command = new CoarctatioStatsCommand();
        assertEquals("coarctatio", command.getName());
        assertTrue(command.getUsage(mock(ICommandSender.class)).startsWith("/coarctatio"));
        // Anyone may run it, since it only reads counters
        assertEquals(0, command.getRequiredPermissionLevel());

        ICommandSender sender = mock(ICommandSender.class);
        command.execute(null, sender, new String[0]);
        Mockito.verify(sender, Mockito.atLeastOnce()).sendMessage(Mockito.any(ITextComponent.class));
    }
}
