package org.lwjgl.opengl;

import org.lwjgl.LWJGLException;

// Test stand-in for LWJGL2's Display, whose real static initialiser opens the X display; every query is a settable field
public final class Display {
    public static boolean visible, active, created, fullscreen, vsync;
    public static DisplayMode displayMode;
    public static DisplayMode[] availableDisplayModes;
    public static Drawable drawable;
    public static int createFailures;
    public static int createCalls;

    private Display() {}

    public static void reset() {
        visible = true;
        active = true;
        created = true;
        fullscreen = false;
        vsync = false;
        displayMode = new DisplayMode(854, 480);
        availableDisplayModes = new DisplayMode[]{displayMode};
        drawable = null;
        createFailures = 0;
        createCalls = 0;
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

    public static Drawable getDrawable() {
        return drawable;
    }

    // Fails the first createFailures calls, for the windowed-retry paths
    public static void create() throws LWJGLException {
        createCalls++;
        if (createFailures > 0) {
            createFailures--;
            throw new LWJGLException("test display refused to create");
        }
        created = true;
    }
}
