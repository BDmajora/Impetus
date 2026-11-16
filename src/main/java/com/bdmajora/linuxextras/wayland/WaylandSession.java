package com.bdmajora.linuxextras.wayland;

import com.bdmajora.linuxextras.LinuxExtras;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

// Detector of a Wayland session.
public final class WaylandSession {
    // Prevents instantiation
    private WaylandSession() {
    }

    // Returns whether a Wayland session is reachable.
    public static boolean available(Map<String, String> env) {
        if (!env.getOrDefault("XDG_SESSION_TYPE", "").toLowerCase(Locale.ROOT).startsWith("wayland")) {
            return false;
        }
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

    // Returns the path of the Wayland socket.
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
