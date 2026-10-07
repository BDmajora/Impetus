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

    // Switches the monitor an exclusive-fullscreen window owns to this mode; Cleanroom's Display.setDisplayModeAndFullscreen is an unimplemented stub, so this is GLFW's own monitor switch. False when the window is not exclusive fullscreen
    public static boolean setFullscreenMode(DisplayMode mode) {
        long window = handle();
        long monitor = window == 0L ? 0L : GLFW.glfwGetWindowMonitor(window);
        if (monitor == 0L) {
            return false;
        }

        int refresh = mode.getFrequency() > 0 ? mode.getFrequency() : GLFW.GLFW_DONT_CARE;
        GLFW.glfwSetWindowMonitor(window, monitor, 0, 0, mode.getWidth(), mode.getHeight(), refresh);
        return true;
    }

    // The monitor's mode when the game started, which is what Cleanroom enters fullscreen at
    public static DisplayMode desktopMode() {
        return Display.getDesktopDisplayMode();
    }

    // An undecorated window exactly covering its monitor at the desktop mode, which Cleanroom's Display can make and LWJGL2's could not
    public static boolean isBorderless() {
        return handle() != 0L && Display.isBorderless();
    }

    // Through Display, which keeps the windowed bounds to return to
    public static void setBorderless(boolean borderless) {
        Display.setBorderless(borderless);
    }
}
