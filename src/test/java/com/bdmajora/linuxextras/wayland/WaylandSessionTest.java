package com.bdmajora.linuxextras.wayland;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WaylandSessionTest {
    @Test
    void theSocketResolvesAsLibwaylandResolvesIt(@TempDir Path dir) {
        // wayland-0 under the runtime directory when nothing names it, an absolute name as given, nothing without a runtime directory
        assertEquals(dir.resolve("wayland-0"), WaylandSession.socket(Map.of("XDG_RUNTIME_DIR", dir.toString())));
        assertEquals(Path.of("/run/elsewhere/sock"), WaylandSession.socket(Map.of("WAYLAND_DISPLAY", "/run/elsewhere/sock")));
        assertNull(WaylandSession.socket(Map.of("WAYLAND_DISPLAY", "wayland-1")));
    }

    @Test
    void onlyAWaylandSessionWithItsCompositorInReachQualifies(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("wayland-1"));
        assertTrue(WaylandSession.available(Map.of("XDG_SESSION_TYPE", "Wayland", "XDG_RUNTIME_DIR", dir.toString(), "WAYLAND_DISPLAY", "wayland-1")));
        assertFalse(WaylandSession.available(Map.of("XDG_SESSION_TYPE", "wayland", "XDG_RUNTIME_DIR", dir.toString(), "WAYLAND_DISPLAY", "wayland-2")));
        assertFalse(WaylandSession.available(Map.of("XDG_SESSION_TYPE", "wayland")));
        assertFalse(WaylandSession.available(Map.of("XDG_SESSION_TYPE", "x11")));
        assertFalse(WaylandSession.available(Map.of()));
        // A connection handed down as a descriptor needs no socket
        assertTrue(WaylandSession.available(Map.of("XDG_SESSION_TYPE", "wayland", "WAYLAND_SOCKET", "5")));
    }
}
