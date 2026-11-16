package com.bdmajora.impetus.impl.platform;

import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.FullscreenMode;
import com.bdmajora.impetus.impl.gui.FullscreenResolutions;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeEarlyConfig;

// Switcher of fullscreen and windowed modes.
public final class WindowModes {
    // Flag set after the saved mode is restored.
    private static boolean restored;

    // Prevents instantiation
    private WindowModes() {
    }

    // Returns the current window mode.
    public static FullscreenMode current(Minecraft client) {
        if (GameWindow.isBorderless()) {
            return FullscreenMode.BORDERLESS;
        }
        if (client.isFullScreen()) {
            return FullscreenMode.EXCLUSIVE;
        }
        return FullscreenMode.OFF;
    }

    // Switches to a mode and returns the mode applied.
    public static FullscreenMode apply(Minecraft client, FullscreenMode target) {
        if (GameWindow.isBorderless() && client.isFullScreen()) {
            toggleExclusive(client);
        } else if (target != FullscreenMode.BORDERLESS && GameWindow.isBorderless()) {
            GameWindow.setBorderless(false);
        }
        if (client.isFullScreen() != (target == FullscreenMode.EXCLUSIVE)) {
            toggleExclusive(client);
        }
        if (target == FullscreenMode.BORDERLESS && !GameWindow.isBorderless()) {
            GameWindow.setBorderless(true);
        }
        return current(client);
    }

    // Toggles fullscreen with the configured mode.
    public static void toggle(Minecraft client, Runnable vanilla) {
        ImpetusGameOptions options = ImpetusVintage.options();
        if (GameWindow.isBorderless() && !ForgeEarlyConfig.WINDOW_BORDERLESS_REPLACES_FULLSCREEN) {
            if (client.isFullScreen()) {
                vanilla.run();
            } else {
                GameWindow.setBorderless(false);
            }
        } else {
            vanilla.run();
            if (client.isFullScreen() && options.fullscreenResolution > 0) {
                FullscreenResolutions.apply(options.fullscreenResolution);
            }
        }
        options.fullscreenMode = current(client);
    }

    // Restores the saved window mode once.
    public static void restoreOnce(Minecraft client, ImpetusGameOptions options) {
        if (restored) {
            return;
        }
        restored = true;

        if (client.isFullScreen()) {
            if (options.fullscreenResolution > 0) {
                FullscreenResolutions.apply(options.fullscreenResolution);
            }
        } else if (options.fullscreenMode == FullscreenMode.BORDERLESS) {
            GameWindow.setBorderless(true);
        }
        options.fullscreenMode = current(client);
    }

    // Toggles exclusive fullscreen.
    private static void toggleExclusive(Minecraft client) {
        boolean override = ForgeEarlyConfig.WINDOW_BORDERLESS_REPLACES_FULLSCREEN;
        ForgeEarlyConfig.WINDOW_BORDERLESS_REPLACES_FULLSCREEN = false;
        try {
            client.toggleFullscreen();
        } finally {
            ForgeEarlyConfig.WINDOW_BORDERLESS_REPLACES_FULLSCREEN = override;
        }
    }
}
