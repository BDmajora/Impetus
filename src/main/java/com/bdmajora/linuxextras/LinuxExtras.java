package com.bdmajora.linuxextras;

import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.linuxextras.wayland.WaylandOutputOrder;
import com.bdmajora.linuxextras.wayland.WaylandSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.sdl.SDLHints;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;

// Entry point of the Linux Extras module.
public final class LinuxExtras {
    // Logger of this class.
    public static final Logger LOGGER = LogManager.getLogger("Impetus/LinuxExtras");

    // Prevents instantiation
    private LinuxExtras() {
    }

    // Returns the loaded settings.
    public static LinuxExtrasConfig options() {
        return LinuxExtrasConfig.get();
    }

    // Applies the window settings before the window opens.
    public static void prepareWindow() {
        prepareWindow(OsKind.current(), System.getenv());
    }

    // Applies the window settings for an OS and environment.
    static void prepareWindow(OsKind os, Map<String, String> env) {
        if (os != OsKind.LINUX || !options().waylandFullscreenMonitor || !WaylandSession.available(env)) {
            return;
        }
        try {
            var names = new LinkedHashSet<>(Arrays.asList(options().waylandMonitors));
            names.addAll(WaylandOutputOrder.outputNames());
            names.removeIf(String::isBlank);
            if (!names.isEmpty() && !SDLHints.SDL_SetHint(SDLHints.SDL_HINT_VIDEO_DISPLAY_PRIORITY, String.join(",", names))) {
                LOGGER.warn("SDL refused the fullscreen display priority");
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Could not set the fullscreen display priority", e);
        }
    }
}
