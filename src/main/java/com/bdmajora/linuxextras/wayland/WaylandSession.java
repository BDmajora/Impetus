package com.bdmajora.linuxextras.wayland;

import com.bdmajora.linuxextras.LinuxExtras;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

// Whether a Wayland compositor is reachable for the primary-monitor query
public final class WaylandSession {
    private WaylandSession() {
    }

    public static boolean available(Map<String, String> env) {
        if (!env.getOrDefault("XDG_SESSION_TYPE", "").toLowerCase(Locale.ROOT).startsWith("wayland")) {
            return false;
        }
        // libwayland takes an already-connected descriptor over the socket path when one is handed down
        if (env.containsKey("WAYLAND_SOCKET")) {
            return true;
        }
        Path socket = socket(env);
        if (socket == null || !Files.exists(socket)) {
            LinuxExtras.LOGGER.warn("The session is Wayland but its compositor socket ({}) is out of reach; the game window stays on XWayland", socket);
            return false;
        }
        return true;
    }

    // Where libwayland connects: WAYLAND_DISPLAY (wayland-0 when unset) under XDG_RUNTIME_DIR unless already absolute; null with no runtime directory to resolve against
    static Path socket(Map<String, String> env) {
        String display = env.getOrDefault("WAYLAND_DISPLAY", "");
        if (display.isEmpty()) {
            display = "wayland-0";
        }
        Path path = Path.of(display);
        if (path.isAbsolute()) {
            return path;
        }
        String runtime = env.getOrDefault("XDG_RUNTIME_DIR", "");
        return runtime.isEmpty() ? null : Path.of(runtime).resolve(path);
    }
}
