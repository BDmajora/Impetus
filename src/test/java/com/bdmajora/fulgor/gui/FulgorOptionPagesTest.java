package com.bdmajora.fulgor.gui;

import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.impetus.api.options.structure.Option;
import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.OptionPages;
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

    @Test
    void theEngineSwitchWritesTheConfigAndNeedsARestart() throws Exception {
        Option<Boolean> enabled = FulgorOptionPages.engine();
        OptionPages.exercise(enabled);
        assertEquals("enabled", enabled.getId().getPath());
        assertTrue(enabled.getFlags().contains(OptionFlag.REQUIRES_GAME_RESTART));

        // Writing a value goes through to the config and then to the file
        assertTrue(enabled.getValue());
        enabled.setValue(false);
        assertTrue(enabled.hasChanged());
        enabled.applyChanges();
        assertFalse(FulgorConfig.get().enabled);
        enabled.getStorage().save();
        assertTrue(Files.readString(dir.resolve("config/impetus-fulgor.cfg")).contains("enabled=false"));
        assertNotNull(enabled.getStorage().getData());
        assertNotNull(Mixins.construct(FulgorOptionPages.class));
    }
}
