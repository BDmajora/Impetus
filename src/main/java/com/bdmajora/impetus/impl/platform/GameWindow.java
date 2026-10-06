package com.bdmajora.impetus.impl.platform;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

// The game window, as one seam: Cleanroom's LWJGL2 bridge (Display) owns the GLFW window and its fullscreen/vsync bookkeeping, so state changes go through it, and every query reads GLFW directly
public final class GameWindow {

    private GameWindow() {
    }

    // The GLFW handle Cleanroom created; 0 before the window exists
    public static long handle() {
        return Display.isCreated() ? Display.getWindow() : 0L;
    }

    // Iconified, or never shown; a frame there is pure waste
    public static boolean isMinimized() {
        long window = handle();
        return window != 0L && (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE
                || GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE);
    }

    // Holds input focus
    public static boolean isFocused() {
        long window = handle();
        return window != 0L && GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
    }

    // Whether the window's GL context is current on the calling thread
    public static boolean isContextCurrent() {
        long window = handle();
        return window != 0L && GLFW.glfwGetCurrentContext() == window;
    }

    // Vanilla's vsync switch, routed through Display so its own record of the setting stays true
    public static void setVsync(boolean enabled) {
        Display.setVSyncEnabled(enabled);
    }

    // Raw swap interval for the current context, e.g. -1 for adaptive sync; Display has no notion of that mode
    public static void setSwapInterval(int interval) {
        GLFW.glfwSwapInterval(interval);
    }

    // WGL/GLX extension query against the current context
    public static boolean platformExtensionSupported(String extension) {
        return GLFW.glfwExtensionSupported(extension);
    }

    // Every video mode of the monitor the window would go fullscreen on
    public static DisplayMode[] fullscreenModes() {
        return Display.getAvailableDisplayModes();
    }

    public static boolean isFullscreen() {
        return Display.isFullscreen();
    }

    // Switches resolution while staying fullscreen; Display remembers the windowed size to return to
    public static void setFullscreenMode(DisplayMode mode) {
        Display.setDisplayModeAndFullscreen(mode);
    }
}
