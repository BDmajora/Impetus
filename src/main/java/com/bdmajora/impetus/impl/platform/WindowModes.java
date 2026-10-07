package com.bdmajora.impetus.impl.platform;

import com.bdmajora.impetus.ImpetusVintage;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions;
import com.bdmajora.impetus.engine.impl.gui.ImpetusGameOptions.FullscreenMode;
import com.bdmajora.impetus.impl.gui.FullscreenResolutions;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeEarlyConfig;

// Impetus's three window modes on Cleanroom's window: exclusive is vanilla's own fullscreen, at the chosen resolution, and borderless is lwjglx's monitor-sized undecorated window, which LWJGL2's Display could not make
public final class WindowModes {
    private static boolean restored;

    private WindowModes() {
    }

    // The mode the window is in right now
    public static FullscreenMode current(Minecraft client) {
        if (client.isFullScreen()) {
            return FullscreenMode.EXCLUSIVE;
        }
        return GameWindow.isBorderless() ? FullscreenMode.BORDERLESS : FullscreenMode.OFF;
    }

    // Moves the window out of whichever mode it is in and into the requested one; answers where it ended up, OFF when the window refused
    public static FullscreenMode apply(Minecraft client, FullscreenMode target) {
        if (target != FullscreenMode.BORDERLESS && GameWindow.isBorderless()) {
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

    // Every caller of vanilla's toggle, F11 included: a borderless window leaves borderless rather than stacking exclusive fullscreen on top, an exclusive one gets the chosen resolution, and the option follows whatever happened
    public static void toggle(Minecraft client, Runnable vanilla) {
        ImpetusGameOptions options = ImpetusVintage.options();
        if (GameWindow.isBorderless() && !ForgeEarlyConfig.WINDOW_BORDERLESS_REPLACES_FULLSCREEN) {
            GameWindow.setBorderless(false);
        } else {
            vanilla.run();
            if (client.isFullScreen() && options.fullscreenResolution > 0) {
                FullscreenResolutions.apply(options.fullscreenResolution);
            }
        }
        options.fullscreenMode = current(client);
    }

    // Once, on the first frame: vanilla's options do not remember borderless, and Cleanroom always enters fullscreen at the desktop mode
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

    // Vanilla's toggle with Cleanroom's own borderless override held off for the call, since with it on the toggle switches borderless instead
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
