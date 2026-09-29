package com.bdmajora.fulgor.gui;

import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionPage;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FulgorOptionPagesTest {
    @TempDir
    Path dir;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.forge();
    }

    @BeforeEach
    void freshConfig() {
        Statics.set(net.minecraft.launchwrapper.Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(FulgorConfig.class, "instance", null);
    }

    // Reads an option and writes the same value back, so both halves of its binding run
    @SuppressWarnings("unchecked")
    private static <T> void touch(Option<T> option) {
        T value = option.getValue();
        option.setValue(value);
        option.applyChanges();
    }

    @Test
    void thePageMirrorsEverySwitchInTheConfig() throws Exception {
        OptionPage page = FulgorOptionPages.lighting();
        assertEquals("lighting", page.getId().getPath());
        assertFalse(page.getGroups().isEmpty());

        int toggles = 0;
        Option<Boolean> enabled = null;
        Option<Boolean> live = null;
        for (OptionGroup group : page.getGroups()) {
            for (Option<?> option : group.getOptions()) {
                toggles++;
                touch(option);
                if (option.getId().getPath().equals("enabled")) {
                    enabled = (Option<Boolean>) option;
                }
                if (option.getId().getPath().equals("async_send_chunks_without_light")) {
                    live = (Option<Boolean>) option;
                }
            }
        }
        assertTrue(toggles >= 10);
        assertNotNull(enabled);
        assertNotNull(live);

        // Most switches decide whether a mixin applies, so they only take effect next launch
        assertTrue(enabled.getFlags().contains(OptionFlag.REQUIRES_GAME_RESTART));
        assertFalse(live.getFlags().contains(OptionFlag.REQUIRES_GAME_RESTART));

        // Writing a value goes through to the config and then to the file
        assertTrue(enabled.getValue());
        enabled.setValue(false);
        assertTrue(enabled.hasChanged());
        enabled.applyChanges();
        assertFalse(FulgorConfig.get().enabled);
        enabled.getStorage().save();
        assertTrue(Files.readString(dir.resolve("config/impetus-fulgor.cfg")).contains("enabled=false"));
        assertNotNull(enabled.getStorage().getData());
    }
}
