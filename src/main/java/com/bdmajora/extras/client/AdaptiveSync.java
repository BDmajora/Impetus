package com.bdmajora.extras.client;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.ExtrasConfig;
import com.bdmajora.impetus.impl.platform.GameWindow;
import net.minecraft.client.Minecraft;

// Adaptive VSync (swap interval -1 honours VSync above the refresh rate, disengages below), set straight through GLFW on Cleanroom's window
public final class AdaptiveSync {
    private static Boolean supported;

    private AdaptiveSync() {
    }

    // Whether the driver advertises tear control, i.e. whether -1 means anything; resolved once since a driver gaining it mid-session is not worth a lookup per frame
    public static boolean isSupported() {
        Boolean cached = supported;
        if (cached != null) {
            return cached;
        }

        boolean result = false;
        try {
            result = GameWindow.platformExtensionSupported("GLX_EXT_swap_control_tear")
                    || GameWindow.platformExtensionSupported("WGL_EXT_swap_control_tear");
        } catch (Throwable t) {
            Extras.LOGGER.warn("Could not determine adaptive VSync support; assuming unsupported", t);
        }

        supported = result;
        return result;
    }

    // The mode currently in effect, derived from the vanilla VSync flag and the adaptive switch.
    public static ExtrasConfig.VerticalSync current() {
        ExtrasConfig options = Extras.options();

        if (options.extra.useAdaptiveSync && isSupported()) {
            return ExtrasConfig.VerticalSync.ADAPTIVE;
        }

        return Minecraft.getMinecraft().gameSettings.enableVsync
                ? ExtrasConfig.VerticalSync.ON
                : ExtrasConfig.VerticalSync.OFF;
    }

    // Applies a mode and updates the vanilla setting so the two never disagree; ADAPTIVE turns vanilla VSync on then overrides the interval, and falls back to plain ON without driver support
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

    // Re-asserts the adaptive interval, since anything toggling vanilla vsync (vanilla video settings, Impetus' VSync tickbox) resets it behind our back
    public static void reapply() {
        if (Extras.options().extra.useAdaptiveSync && isSupported()) {
            setSwapInterval(-1);
        }
    }

    // Sets the interval on the current context; a failure is logged and adaptive sync switched off
    private static void setSwapInterval(int interval) {
        try {
            GameWindow.setSwapInterval(interval);
        } catch (Throwable t) {
            Extras.LOGGER.warn("Could not set the swap interval; disabling adaptive VSync", t);
            supported = false;
            Extras.options().extra.useAdaptiveSync = false;
        }
    }
}
