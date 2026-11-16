package com.bdmajora.extras.client;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.impetus.impl.platform.GameWindow;
import net.minecraft.client.Minecraft;

// Adaptive vertical sync support.
public final class AdaptiveSync {
    // Whether the driver supports adaptive sync or null before the first check.
    private static Boolean supported;

    // Prevents instantiation
    private AdaptiveSync() {
    }

    // Returns whether the driver supports adaptive sync.
    public static boolean isSupported() {
        Boolean cached = supported;
        if (cached != null) {
            return cached;
        }

        if (!GameWindow.isContextCurrent()) {
            return false;
        }
        boolean result = false;
        try {
            result = GameWindow.supportsAdaptiveSync();
        } catch (Throwable t) {
            Extras.LOGGER.warn("Could not determine adaptive VSync support; assuming unsupported", t);
        }

        supported = result;
        return result;
    }

    // Returns the active vertical sync mode.
    public static ExtrasConfig.VerticalSync current() {
        ExtrasConfig options = Extras.options();

        if (options.extra.useAdaptiveSync && isSupported()) {
            return ExtrasConfig.VerticalSync.ADAPTIVE;
        }

        return Minecraft.getMinecraft().gameSettings.enableVsync
                ? ExtrasConfig.VerticalSync.ON
                : ExtrasConfig.VerticalSync.OFF;
    }

    // Applies the vertical sync mode and saves the options.
    public static void apply(ExtrasConfig.VerticalSync mode) {
        ExtrasConfig options = Extras.options();
        Minecraft minecraft = Minecraft.getMinecraft();

        boolean adaptive = mode == ExtrasConfig.VerticalSync.ADAPTIVE && isSupported();
        boolean vsync = mode != ExtrasConfig.VerticalSync.OFF;

        options.extra.useAdaptiveSync = adaptive;
        minecraft.gameSettings.enableVsync = vsync;
        GameWindow.setVsync(vsync);

        if (adaptive) {
            setSwapInterval(-1);
        }

        minecraft.gameSettings.saveOptions();
    }

    // Sets the adaptive swap interval again when adaptive sync is on.
    public static void reapply() {
        if (Extras.options().extra.useAdaptiveSync && isSupported()) {
            setSwapInterval(-1);
        }
    }

    // Sets the swap interval and turns adaptive sync off on failure.
    private static void setSwapInterval(int interval) {
        try {
            if (!GameWindow.setSwapInterval(interval)) {
                throw new IllegalStateException("SDL refused the swap interval");
            }
        } catch (Throwable t) {
            Extras.LOGGER.warn("Could not set the swap interval; disabling adaptive VSync", t);
            supported = false;
            Extras.options().extra.useAdaptiveSync = false;
            GameWindow.setVsync(Minecraft.getMinecraft().gameSettings.enableVsync);
        }
    }
}
