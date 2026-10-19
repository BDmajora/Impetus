package com.bdmajora.impetus.impl.platform;

import com.cleanroommc.client.sdl.SDL;
import com.cleanroommc.client.sdl.Window;
import com.cleanroommc.client.sdl.video.DisplayMode;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.system.MemoryStack;

// Cleanroom owns the SDL window and its state; all renderer window operations go through this seam
public final class GameWindow {
    private GameWindow() {
    }

    public static long handle() {
        Window window = SDL.window();
        return window == null ? 0L : window.handle();
    }

    public static boolean isMinimized() {
        Window window = SDL.window();
        return window != null && (window.minimized() || window.hidden());
    }

    public static boolean isFocused() {
        Window window = SDL.window();
        return window != null && window.focused();
    }

    public static boolean isContextCurrent() {
        long window = handle();
        return window != 0L && SDLVideo.SDL_GL_GetCurrentWindow() == window;
    }

    public static void setVsync(boolean enabled) {
        Window window = SDL.window();
        if (window != null) {
            window.vsync(enabled);
        }
    }

    public static boolean setSwapInterval(int interval) {
        return isContextCurrent() && SDLVideo.SDL_GL_SetSwapInterval(interval);
    }

    // SDL reports adaptive support by accepting -1; put the previous interval back after the probe
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

    public static DisplayMode[] fullscreenModes() {
        Window window = SDL.window();
        var display = window == null ? null : window.display();
        return display == null ? new DisplayMode[0] : display.modes().toArray(DisplayMode[]::new);
    }

    public static boolean isFullscreen() {
        Window window = SDL.window();
        return window != null && window.fullscreen();
    }

    // Resolve on the current display, since a saved resolution may have come from another monitor
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

    public static DisplayMode desktopMode() {
        Window window = SDL.window();
        var display = window == null ? null : window.display();
        return display == null ? null : display.desktopMode();
    }

    // SDL desktop fullscreen has no exclusive mode; coverDisplay is Cleanroom's Windows compatibility path
    public static boolean isBorderless() {
        Window window = SDL.window();
        return window != null && window.fullscreen()
                && (window.borderless() || SDLVideo.SDL_GetWindowFullscreenMode(window.handle()) == null);
    }

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
