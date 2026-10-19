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

// SDL handles native Wayland and icons; Linux Extras supplies KDE's primary display order before video starts
public final class LinuxExtras {
    public static final Logger LOGGER = LogManager.getLogger("Impetus/LinuxExtras");

    private LinuxExtras() {
    }

    public static LinuxExtrasConfig options() {
        return LinuxExtrasConfig.get();
    }

    public static void prepareWindow() {
        prepareWindow(OsKind.current(), System.getenv());
    }

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
