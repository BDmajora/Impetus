package com.bdmajora.impetus.impl.platform;

import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.FullscreenMode;
import com.bdmajora.impetus.impl.gui.FullscreenResolutions;
import com.bdmajora.impetus.mixin.core.FullscreenToggleMixin;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeEarlyConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WindowModesTest {
    private final boolean override = ForgeEarlyConfig.WINDOW_BORDERLESS_REPLACES_FULLSCREEN;
    private final AtomicBoolean fullscreen = new AtomicBoolean();
    private Minecraft client;
    private ImpetusGameOptions options;

    @BeforeEach
    void windowed() {
        Display.reset();
        client = Mc.client();
        when(client.isFullScreen()).thenAnswer(inv -> fullscreen.get());
        // Vanilla's toggle flips exclusive fullscreen, and must never see Cleanroom's borderless override on
        doAnswer(inv -> {
            assertFalse(ForgeEarlyConfig.WINDOW_BORDERLESS_REPLACES_FULLSCREEN);
            fullscreen.set(!fullscreen.get());
            return null;
        }).when(client).toggleFullscreen();
        options = ImpetusVintage.options();
        Statics.set(WindowModes.class, "restored", false);
        Statics.set(FullscreenResolutions.class, "modes", null);
    }

    @AfterEach
    void restore() {
        Display.reset();
        ForgeEarlyConfig.WINDOW_BORDERLESS_REPLACES_FULLSCREEN = override;
        options.fullscreenMode = FullscreenMode.OFF;
        options.fullscreenResolution = 0;
        Statics.set(FullscreenResolutions.class, "modes", null);
    }

    private static DisplayMode reported(int width, int height) throws ReflectiveOperationException {
        // The package-private constructor is the one LWJGL uses for modes the monitor reports, which are fullscreen capable
        Constructor<DisplayMode> ctor = DisplayMode.class.getDeclaredConstructor(int.class, int.class, int.class, int.class);
        ctor.setAccessible(true);
        return ctor.newInstance(width, height, 32, 60);
    }

    @Test
    void eachModeIsReachedFromEveryOther() {
        // Borderless from windowed is Display's alone
        assertEquals(FullscreenMode.BORDERLESS, WindowModes.apply(client, FullscreenMode.BORDERLESS));
        assertTrue(Display.borderless);
        verify(client, never()).toggleFullscreen();
        assertEquals(FullscreenMode.BORDERLESS, WindowModes.apply(client, FullscreenMode.BORDERLESS));

        // Exclusive from borderless leaves borderless first; Cleanroom's override is held off for the toggle and put back after
        ForgeEarlyConfig.WINDOW_BORDERLESS_REPLACES_FULLSCREEN = true;
        assertEquals(FullscreenMode.EXCLUSIVE, WindowModes.apply(client, FullscreenMode.EXCLUSIVE));
        assertFalse(Display.borderless);
        assertTrue(ForgeEarlyConfig.WINDOW_BORDERLESS_REPLACES_FULLSCREEN);

        // Borderless from exclusive leaves exclusive first, then windowed again
        assertEquals(FullscreenMode.BORDERLESS, WindowModes.apply(client, FullscreenMode.BORDERLESS));
        assertFalse(fullscreen.get());
        assertEquals(FullscreenMode.OFF, WindowModes.apply(client, FullscreenMode.OFF));
        assertFalse(Display.borderless);
        verify(client, times(2)).toggleFullscreen();
    }

    @Test
    void theToggleLeavesBorderlessOrEntersExclusiveAtTheChosenResolution() throws Exception {
        Runnable vanilla = client::toggleFullscreen;

        // F11 on a borderless window leaves borderless instead of stacking exclusive fullscreen on it
        Display.borderless = true;
        options.fullscreenMode = FullscreenMode.BORDERLESS;
        WindowModes.toggle(client, vanilla);
        assertFalse(Display.borderless);
        assertFalse(fullscreen.get());
        assertEquals(FullscreenMode.OFF, options.fullscreenMode);

        // With Cleanroom's own override on, vanilla's toggle is the borderless switch and is left to do it
        ForgeEarlyConfig.WINDOW_BORDERLESS_REPLACES_FULLSCREEN = true;
        WindowModes.toggle(client, () -> Display.borderless = !Display.borderless);
        assertEquals(FullscreenMode.BORDERLESS, options.fullscreenMode);
        Display.borderless = false;
        ForgeEarlyConfig.WINDOW_BORDERLESS_REPLACES_FULLSCREEN = false;

        // Entering exclusive at "Current" keeps Cleanroom's desktop mode; a chosen resolution is switched to
        WindowModes.toggle(client, vanilla);
        assertEquals(FullscreenMode.EXCLUSIVE, options.fullscreenMode);
        WindowModes.toggle(client, vanilla);
        Display.availableDisplayModes = new DisplayMode[] {reported(1280, 720)};
        options.fullscreenResolution = 1;
        try (MockedStatic<GLFW> glfw = Mockito.mockStatic(GLFW.class)) {
            glfw.when(() -> GLFW.glfwGetWindowMonitor(Display.window)).thenReturn(5L);
            WindowModes.toggle(client, vanilla);
            glfw.verify(() -> GLFW.glfwSetWindowMonitor(Display.window, 5L, 0, 0, 1280, 720, 60));
        }
        verify(client).resize(1280, 720);
        assertEquals(FullscreenMode.EXCLUSIVE, options.fullscreenMode);

        // The mixin hands vanilla's method to the same path
        WindowModes.toggle(client, vanilla);
        Mc.Recorded<Void> original = Mc.operation();
        Mixins.call(Mixins.instance(FullscreenToggleMixin.class), "impetus$toggleFullscreen", original);
        assertEquals(1, original.count());
    }

    @Test
    void theFirstFrameRestoresBorderlessOrTheResolutionOnce() throws Exception {
        // Vanilla's options do not remember borderless, so the first frame brings it back, and only the first
        options.fullscreenMode = FullscreenMode.BORDERLESS;
        WindowModes.restoreOnce(client, options);
        assertTrue(Display.borderless);
        Display.borderless = false;
        WindowModes.restoreOnce(client, options);
        assertFalse(Display.borderless);

        // A game started fullscreen is at the desktop mode until the chosen resolution is applied
        Statics.set(WindowModes.class, "restored", false);
        fullscreen.set(true);
        Display.availableDisplayModes = new DisplayMode[] {reported(1600, 900)};
        options.fullscreenResolution = 1;
        try (MockedStatic<GLFW> glfw = Mockito.mockStatic(GLFW.class)) {
            glfw.when(() -> GLFW.glfwGetWindowMonitor(Display.window)).thenReturn(5L);
            WindowModes.restoreOnce(client, options);
            glfw.verify(() -> GLFW.glfwSetWindowMonitor(Display.window, 5L, 0, 0, 1600, 900, 60));
        }
        assertEquals(FullscreenMode.EXCLUSIVE, options.fullscreenMode);

        // At "Current" it is left exactly as Cleanroom made it, and a windowed start stays windowed
        Statics.set(WindowModes.class, "restored", false);
        options.fullscreenResolution = 0;
        WindowModes.restoreOnce(client, options);
        Statics.set(WindowModes.class, "restored", false);
        fullscreen.set(false);
        options.fullscreenMode = FullscreenMode.OFF;
        WindowModes.restoreOnce(client, options);
        assertFalse(Display.borderless);
        assertEquals(FullscreenMode.OFF, options.fullscreenMode);
        assertNotNull(Mixins.construct(WindowModes.class));
    }
}
