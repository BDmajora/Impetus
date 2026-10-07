package org.lwjgl.opengl;

// Test stand-in for Cleanroom's lwjglx Display, whose real static initialiser opens GLFW; every query is a settable field
public final class Display {
    public static boolean visible, active, created, fullscreen, vsync, borderless;
    public static DisplayMode displayMode;
    public static DisplayMode[] availableDisplayModes;
    public static DisplayMode desktopDisplayMode;
    public static long window;

    private Display() {}

    public static void reset() {
        visible = true;
        active = true;
        created = true;
        fullscreen = false;
        vsync = false;
        borderless = false;
        displayMode = new DisplayMode(854, 480);
        desktopDisplayMode = new DisplayMode(1920, 1080);
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

    public static DisplayMode getDesktopDisplayMode() {
        return desktopDisplayMode;
    }

    public static boolean isBorderless() {
        return borderless;
    }

    public static void setBorderless(boolean value) {
        borderless = value;
    }

    // Cleanroom's GLFW window handle
    public static long getWindow() {
        return window;
    }
}
