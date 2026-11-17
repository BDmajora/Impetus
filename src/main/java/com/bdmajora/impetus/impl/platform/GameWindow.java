package com.bdmajora.impetus.impl.platform;

import com.cleanroommc.client.sdl.SDL;
import com.cleanroommc.client.sdl.Window;
import com.cleanroommc.client.sdl.video.DisplayMode;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;

// Access to the native game window.
public final class GameWindow {
    // Prevents instantiation
    private GameWindow() {
    }

    // Returns the native window handle.
    public static long handle() {
        Window window = SDL.window();
        return window == null ? 0L : window.handle();
    }

    // Returns whether the window is minimized or hidden.
    public static boolean isMinimized() {
        Window window = SDL.window();
        return window != null && (window.minimized() || window.hidden());
    }

    // Returns whether the window has input focus.
    public static boolean isFocused() {
        Window window = SDL.window();
        return window != null && window.focused();
    }

    // Returns whether the game window's GL context is current on this thread.
    public static boolean isContextCurrent() {
        long window = handle();
        return window != 0L && SDLVideo.SDL_GL_GetCurrentWindow() == window;
    }

    // Stores the vsync.
    public static void setVsync(boolean enabled) {
        Window window = SDL.window();
        if (window != null) {
            window.vsync(enabled);
        }
    }

    // Stores the swap interval.
    public static boolean setSwapInterval(int interval) {
        return isContextCurrent() && SDLVideo.SDL_GL_SetSwapInterval(interval);
    }

    // Returns whether the driver accepts adaptive vsync without changing the current interval.
    public static boolean supportsAdaptiveSync() {
        if (!isContextCurrent()) {
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var interval = stack.mallocInt(1);
            if (!SDLVideo.SDL_GL_GetSwapInterval(interval)) {
                return false;
            }
            boolean supported = SDLVideo.SDL_GL_SetSwapInterval(-1);
            SDLVideo.SDL_GL_SetSwapInterval(interval.get(0));
            return supported;
        }
    }

    // Returns the fullscreen display modes.
    public static DisplayMode[] fullscreenModes() {
        Window window = SDL.window();
        var display = window == null ? null : window.display();
        return display == null ? new DisplayMode[0] : display.modes().toArray(DisplayMode[]::new);
    }

    // Returns whether the window is exclusive or borderless fullscreen.
    public static boolean isFullscreen() {
        Window window = SDL.window();
        return window != null && window.fullscreen();
    }

    // Stores the fullscreen mode.
    public static boolean setFullscreenMode(DisplayMode mode) {
        Window window = SDL.window();
        if (mode == null || window == null || !window.fullscreen() || isBorderless()) {
            return false;
        }
        var display = window.display();
        if (display != null) {
            for (DisplayMode available : display.modes()) {
                if (available.matches(mode.width(), mode.height(), mode.refreshRate())) {
                    window.fullscreenMode(available);
                    return true;
                }
            }
        }
        return false;
    }

    // Returns the desktop display mode.
    public static DisplayMode desktopMode() {
        Window window = SDL.window();
        var display = window == null ? null : window.display();
        return display == null ? null : display.desktopMode();
    }

    // Returns whether the window is borderless fullscreen rather than exclusive.
    public static boolean isBorderless() {
        Window window = SDL.window();
        return window != null && window.fullscreen()
                && (window.borderless() || SDLVideo.SDL_GetWindowFullscreenMode(window.handle()) == null);
    }

    // Stores the borderless.
    public static void setBorderless(boolean borderless) {
        Window window = SDL.window();
        if (window != null) {
            if (borderless) {
                window.fullscreenMode(null);
            } else {
                window.fullscreen(false);
            }
        }
    }
}
