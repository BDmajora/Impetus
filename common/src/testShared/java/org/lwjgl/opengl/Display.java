package org.lwjgl.opengl;

// Test stand-in for Display, both LWJGL2's and Cleanroom's lwjglx bridge (whose real static initialisers open the X display or GLFW); every query is a settable field
public final class Display {
    public static boolean visible, active, created, fullscreen, vsync;
    public static DisplayMode displayMode;
    public static DisplayMode[] availableDisplayModes;
    public static long window;

    private Display() {}

    public static void reset() {
        visible = true;
        active = true;
        created = true;
        fullscreen = false;
        vsync = false;
        displayMode = new DisplayMode(854, 480);
        availableDisplayModes = new DisplayMode[]{displayMode};
        window = 1L;
    }

    public static boolean isVisible() {
        return visible;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean isCreated() {
        return created;
    }

    public static boolean isFullscreen() {
        return fullscreen;
    }

    public static void setFullscreen(boolean value) {
        fullscreen = value;
    }

    public static void setVSyncEnabled(boolean value) {
        vsync = value;
    }

    public static void setDisplayMode(DisplayMode mode) {
        displayMode = mode;
    }

    public static void setDisplayModeAndFullscreen(DisplayMode mode) {
        displayMode = mode;
        fullscreen = true;
    }

    public static DisplayMode getDisplayMode() {
        return displayMode;
    }

    public static DisplayMode[] getAvailableDisplayModes() {
        return availableDisplayModes;
    }

    // Cleanroom's GLFW window handle
    public static long getWindow() {
        return window;
    }
}
