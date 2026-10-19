package com.bdmajora.extras.client.budget;

import com.bdmajora.extras.Extras;
import com.bdmajora.impetus.impl.gui.FullscreenResolutions;
import com.bdmajora.impetus.impl.gui.ImpetusVideoOptionsScreen;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.OptionsScreens;
import com.bdmajora.testing.Statics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.bdmajora.impetus.impl.platform.GameWindow;
import com.cleanroommc.client.sdl.video.DisplayMode;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RenderBudgetKeysTest {
    @TempDir
    static Path home;

    private static Minecraft client;
    private static GameSettings settings;

    private MockedStatic<GL11> gl11;
    private MockedStatic<GL13> gl13;
    private MockedStatic<GameWindow> display;

    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
        Mc.textures();
        Mc.forge();
        client = Mc.client();
        settings = Mc.uninitialized(GameSettings.class);
        Mixins.set(client, "gameSettings", settings);
        client.displayWidth = 854;
        client.displayHeight = 480;
        OptionsScreens.prepare(client, home);
        when(client.getSoundHandler()).thenReturn(mock(SoundHandler.class));
    }

    @AfterAll
    static void forgetOptions() {
        OptionsScreens.forget();
    }

    @BeforeEach
    void mockNatives() {
        gl11 = Mockito.mockStatic(GL11.class);
        gl13 = Mockito.mockStatic(GL13.class);
        display = Mockito.mockStatic(GameWindow.class);
        // F9 opens settings, including the fullscreen resolution selector
        display.when(GameWindow::fullscreenModes).thenReturn(new DisplayMode[0]);
        Statics.set(FullscreenResolutions.class, "modes", null);
        Mockito.clearInvocations(client, client.fontRenderer);
    }

    @AfterEach
    void releaseNatives() {
        display.close();
        gl13.close();
        gl11.close();
        client.player = null;
        client.world = null;
        client.currentScreen = null;
    }

    @Test
    void theKeysToggleTheOverlayAndOpenTheSettings() {
        KeyBinding toggle = Statics.get(RenderBudgetKeys.class, "TOGGLE_OVERLAY");
        KeyBinding open = Statics.get(RenderBudgetKeys.class, "OPEN_SETTINGS");
        RenderBudgetKeys.register();
        assertTrue(Arrays.asList(settings.keyBindings).containsAll(List.of(toggle, open)));

        // Every queued press flips the overlay once
        boolean overlay = Extras.options().renderBudget.overlay;
        Mixins.set(toggle, "pressTime", 2);
        RenderBudgetKeys.onKeyInput(new InputEvent.KeyInputEvent());
        assertEquals(overlay, Extras.options().renderBudget.overlay);
        Mixins.set(toggle, "pressTime", 1);
        RenderBudgetKeys.onKeyInput(new InputEvent.KeyInputEvent());
        assertEquals(!overlay, Extras.options().renderBudget.overlay);

        // The settings key opens the full page over whatever is showing
        client.currentScreen = mock(GuiScreen.class);
        Mixins.set(open, "pressTime", 1);
        RenderBudgetKeys.onKeyInput(new InputEvent.KeyInputEvent());
        verify(client).displayGuiScreen(any(ImpetusVideoOptionsScreen.class));
        assertNotNull(Mixins.construct(RenderBudgetKeys.class));
    }
}
