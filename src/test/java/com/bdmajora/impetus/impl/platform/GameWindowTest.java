package com.bdmajora.impetus.impl.platform;

import com.bdmajora.testing.Mixins;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class GameWindowTest {
    private static final long WINDOW = 0xCAFEL;

    @Test
    void queriesReadGlfwAgainstCleanroomsWindow() {
        Display.window = WINDOW;
        try (MockedStatic<GLFW> glfw = Mockito.mockStatic(GLFW.class)) {
            assertEquals(WINDOW, GameWindow.handle());
            // Visible and not iconified is neither minimised nor, without focus, focused
            glfw.when(() -> GLFW.glfwGetWindowAttrib(WINDOW, GLFW.GLFW_VISIBLE)).thenReturn(GLFW.GLFW_TRUE);
            assertFalse(GameWindow.isMinimized());
            assertFalse(GameWindow.isFocused());
            glfw.when(() -> GLFW.glfwGetWindowAttrib(WINDOW, GLFW.GLFW_ICONIFIED)).thenReturn(GLFW.GLFW_TRUE);
            assertTrue(GameWindow.isMinimized());
            // A window never shown counts as minimised too
            glfw.when(() -> GLFW.glfwGetWindowAttrib(WINDOW, GLFW.GLFW_ICONIFIED)).thenReturn(GLFW.GLFW_FALSE);
            glfw.when(() -> GLFW.glfwGetWindowAttrib(WINDOW, GLFW.GLFW_VISIBLE)).thenReturn(GLFW.GLFW_FALSE);
            assertTrue(GameWindow.isMinimized());
            glfw.when(() -> GLFW.glfwGetWindowAttrib(WINDOW, GLFW.GLFW_FOCUSED)).thenReturn(GLFW.GLFW_TRUE);
            assertTrue(GameWindow.isFocused());
            // The context counts as current only when it is this window's
            glfw.when(GLFW::glfwGetCurrentContext).thenReturn(7L);
            assertFalse(GameWindow.isContextCurrent());
            glfw.when(GLFW::glfwGetCurrentContext).thenReturn(WINDOW);
            assertTrue(GameWindow.isContextCurrent());

            GameWindow.setSwapInterval(-1);
            glfw.verify(() -> GLFW.glfwSwapInterval(-1));
            glfw.when(() -> GLFW.glfwExtensionSupported("GLX_EXT_swap_control_tear")).thenReturn(true);
            assertTrue(GameWindow.platformExtensionSupported("GLX_EXT_swap_control_tear"));
            assertFalse(GameWindow.platformExtensionSupported("WGL_EXT_swap_control_tear"));

            // Before Cleanroom creates the window there is no handle, and every query says no without asking GLFW
            Display.created = false;
            assertEquals(0L, GameWindow.handle());
            assertFalse(GameWindow.isMinimized());
            assertFalse(GameWindow.isFocused());
            assertFalse(GameWindow.isContextCurrent());
        }
    }

    @Test
    void stateChangesGoThroughDisplaySoItsBookkeepingHolds() {
        GameWindow.setVsync(true);
        assertTrue(Display.vsync);
        DisplayMode big = new DisplayMode(2560, 1440);
        Display.availableDisplayModes = new DisplayMode[] {big};
        assertArrayEquals(new DisplayMode[] {big}, GameWindow.fullscreenModes());
        assertFalse(GameWindow.isFullscreen());
        assertSame(Display.desktopDisplayMode, GameWindow.desktopMode());
        // Borderless is Display's, and only answers yes once the window exists
        GameWindow.setBorderless(true);
        assertTrue(GameWindow.isBorderless());
        Display.created = false;
        assertFalse(GameWindow.isBorderless());
        Display.reset();
        assertNotNull(Mixins.construct(GameWindow.class));
    }

    @Test
    void fullscreenModesSwitchTheMonitorTheWindowOwns() {
        Display.reset();
        Display.window = WINDOW;
        DisplayMode mode = new DisplayMode(1280, 720);
        try (MockedStatic<GLFW> glfw = Mockito.mockStatic(GLFW.class)) {
            // A windowed (or borderless) window owns no monitor, so there is nothing to switch
            assertFalse(GameWindow.setFullscreenMode(mode));
            glfw.when(() -> GLFW.glfwGetWindowMonitor(WINDOW)).thenReturn(5L);
            // A mode without a refresh rate lets GLFW pick one
            assertTrue(GameWindow.setFullscreenMode(mode));
            glfw.verify(() -> GLFW.glfwSetWindowMonitor(WINDOW, 5L, 0, 0, 1280, 720, GLFW.GLFW_DONT_CARE));
            DisplayMode fast = Mockito.mock(DisplayMode.class);
            Mockito.when(fast.getWidth()).thenReturn(1920);
            Mockito.when(fast.getHeight()).thenReturn(1080);
            Mockito.when(fast.getFrequency()).thenReturn(144);
            assertTrue(GameWindow.setFullscreenMode(fast));
            glfw.verify(() -> GLFW.glfwSetWindowMonitor(WINDOW, 5L, 0, 0, 1920, 1080, 144));
            // No window at all
            Display.created = false;
            assertFalse(GameWindow.setFullscreenMode(mode));
        } finally {
            Display.reset();
        }
    }
}
