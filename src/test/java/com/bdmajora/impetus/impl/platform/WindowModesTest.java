package com.bdmajora.impetus.impl.platform;

import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.FullscreenMode;
import com.bdmajora.impetus.impl.gui.FullscreenResolutions;
import com.bdmajora.impetus.mixin.core.FullscreenToggleMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import com.cleanroommc.client.sdl.video.DisplayMode;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeEarlyConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WindowModesTest {
    private final boolean override = ForgeEarlyConfig.WINDOW_BORDERLESS_REPLACES_FULLSCREEN;
    private final AtomicBoolean fullscreen = new AtomicBoolean();
    private final AtomicBoolean borderless = new AtomicBoolean();
    private Minecraft client;
    private ImpetusGameOptions options;
    private MockedStatic<GameWindow> window;

    @BeforeEach
    void windowed() {
        client = Mc.client();
        when(client.isFullScreen()).thenAnswer(inv -> fullscreen.get());
        doAnswer(inv -> {
            assertFalse(ForgeEarlyConfig.WINDOW_BORDERLESS_REPLACES_FULLSCREEN);
            fullscreen.set(!fullscreen.get());
            borderless.set(false);
            return null;
        }).when(client).toggleFullscreen();
        window = mockStatic(GameWindow.class);
        window.when(GameWindow::isBorderless).thenAnswer(inv -> borderless.get());
        window.when(() -> GameWindow.setBorderless(anyBoolean())).thenAnswer(inv -> {
            borderless.set(inv.getArgument(0));
            return null;
        });
        options = ImpetusVintage.options();
        options.fullscreenResolution = 0;
        ForgeEarlyConfig.WINDOW_BORDERLESS_REPLACES_FULLSCREEN = false;
        Statics.set(WindowModes.class, "restored", false);
        Statics.set(FullscreenResolutions.class, "modes", null);
    }

    @AfterEach
    void restore() {
        if (window != null) window.close();
        ForgeEarlyConfig.WINDOW_BORDERLESS_REPLACES_FULLSCREEN = override;
        options.fullscreenMode = FullscreenMode.OFF;
        options.fullscreenResolution = 0;
        Statics.set(FullscreenResolutions.class, "modes", null);
    }

    @Test
    void eachModeIsReachedFromEveryOther() {
        assertEquals(FullscreenMode.BORDERLESS, WindowModes.apply(client, FullscreenMode.BORDERLESS));
        verify(client, never()).toggleFullscreen();
        assertEquals(FullscreenMode.BORDERLESS, WindowModes.apply(client, FullscreenMode.BORDERLESS));
        ForgeEarlyConfig.WINDOW_BORDERLESS_REPLACES_FULLSCREEN = true;
        assertEquals(FullscreenMode.EXCLUSIVE, WindowModes.apply(client, FullscreenMode.EXCLUSIVE));
        assertFalse(borderless.get());
        assertTrue(ForgeEarlyConfig.WINDOW_BORDERLESS_REPLACES_FULLSCREEN);
        assertEquals(FullscreenMode.BORDERLESS, WindowModes.apply(client, FullscreenMode.BORDERLESS));
        assertFalse(fullscreen.get());
        assertEquals(FullscreenMode.OFF, WindowModes.apply(client, FullscreenMode.OFF));
        assertFalse(borderless.get());
        verify(client, times(2)).toggleFullscreen();
    }

    @Test
    void cleanroomsBorderlessOverrideCanSwitchToRealExclusiveFullscreen() {
        borderless.set(true);
        fullscreen.set(true);
        assertEquals(FullscreenMode.BORDERLESS, WindowModes.current(client));
        assertEquals(FullscreenMode.EXCLUSIVE, WindowModes.apply(client, FullscreenMode.EXCLUSIVE));
        verify(client, times(2)).toggleFullscreen();
        assertFalse(borderless.get());
        assertTrue(fullscreen.get());
    }

    @Test
    void f11LeavesBorderlessAndUsesTheChosenExclusiveResolution() {
        borderless.set(true);
        WindowModes.toggle(client, client::toggleFullscreen);
        assertEquals(FullscreenMode.OFF, options.fullscreenMode);
        assertFalse(borderless.get());
        DisplayMode mode = new DisplayMode(1, 0, 1280, 720, 1, 60);
        window.when(GameWindow::fullscreenModes).thenReturn(new DisplayMode[] {mode});
        window.when(() -> GameWindow.setFullscreenMode(mode)).thenReturn(true);
        options.fullscreenResolution = 1;
        WindowModes.toggle(client, client::toggleFullscreen);
        window.verify(() -> GameWindow.setFullscreenMode(mode));
        verify(client).resize(1280, 720);
        assertEquals(FullscreenMode.EXCLUSIVE, options.fullscreenMode);
        Mc.Recorded<Void> original = Mc.operation();
        Mixins.call(Mixins.instance(FullscreenToggleMixin.class), "impetus$toggleFullscreen", original);
        assertEquals(1, original.count());
    }

    @Test
    void theFirstFrameRestoresBorderlessOrTheResolutionOnce() {
        options.fullscreenMode = FullscreenMode.BORDERLESS;
        WindowModes.restoreOnce(client, options);
        assertTrue(borderless.get());
        borderless.set(false);
        WindowModes.restoreOnce(client, options);
        assertFalse(borderless.get());
        Statics.set(WindowModes.class, "restored", false);
        fullscreen.set(true);
        DisplayMode mode = new DisplayMode(1, 0, 1600, 900, 1, 60);
        window.when(GameWindow::fullscreenModes).thenReturn(new DisplayMode[] {mode});
        window.when(() -> GameWindow.setFullscreenMode(mode)).thenReturn(true);
        options.fullscreenResolution = 1;
        WindowModes.restoreOnce(client, options);
        verify(client).resize(1600, 900);
        assertEquals(FullscreenMode.EXCLUSIVE, options.fullscreenMode);
    }
}
