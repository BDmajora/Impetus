package com.bdmajora.linuxextras;

import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.linuxextras.wayland.WaylandOutputOrder;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.launchwrapper.Launch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.sdl.SDLHints;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LinuxExtrasTest {
    @TempDir Path dir;
    private File realHome;

    @BeforeEach
    void freshConfig() {
        realHome = Launch.minecraftHome;
        Statics.set(Launch.class, "minecraftHome", dir.toFile());
        Mixins.set(LinuxExtrasConfig.class, "instance", null);
    }

    @AfterEach
    void restore() {
        Statics.set(Launch.class, "minecraftHome", realHome);
        Mixins.set(LinuxExtrasConfig.class, "instance", null);
    }

    @Test
    void theConfigPreservesMonitorPreferencesWhileDroppingObsoleteGlfwSwitches() throws IOException {
        LinuxExtrasConfig defaults = LinuxExtras.options();
        assertTrue(defaults.waylandFullscreenMonitor);
        assertTrue(defaults.xdgOpen);
        assertArrayEquals(new String[0], defaults.waylandMonitors);
        Path config = dir.resolve("config/impetus-linuxextras.cfg");
        Files.writeString(config, "forceWayland=false\nwaylandMonitors=DP-2, HDMI-A-1\nwaylandWindowIcon=false\nxdgOpen=false\n");
        Mixins.set(LinuxExtrasConfig.class, "instance", null);
        LinuxExtrasConfig edited = LinuxExtras.options();
        assertArrayEquals(new String[] {"DP-2", "HDMI-A-1"}, edited.waylandMonitors);
        assertFalse(edited.xdgOpen);
        assertTrue(edited.waylandFullscreenMonitor);
        assertSame(edited, LinuxExtras.options());
    }

    @Test
    void sdlReceivesPreferredOutputsThenKdesOrderBeforeInitialization() throws IOException {
        Files.createFile(dir.resolve("wayland-1"));
        Map<String, String> wayland = Map.of("XDG_SESSION_TYPE", "wayland", "XDG_RUNTIME_DIR", dir.toString(), "WAYLAND_DISPLAY", "wayland-1");
        try (MockedStatic<SDLHints> hints = Mockito.mockStatic(SDLHints.class);
             MockedStatic<WaylandOutputOrder> order = Mockito.mockStatic(WaylandOutputOrder.class)) {
            LinuxExtras.prepareWindow(OsKind.WINDOWS, wayland);
            LinuxExtras.prepareWindow(OsKind.LINUX, Map.of("XDG_SESSION_TYPE", "x11"));
            LinuxExtras.options().waylandFullscreenMonitor = false;
            LinuxExtras.prepareWindow(OsKind.LINUX, wayland);
            hints.verifyNoInteractions();
            order.verifyNoInteractions();
            LinuxExtras.options().waylandFullscreenMonitor = true;
            LinuxExtras.options().waylandMonitors = new String[] {"HDMI-A-1", "DP-2"};
            order.when(WaylandOutputOrder::outputNames).thenReturn(List.of("DP-2", "DP-1", "HDMI-A-1"));
            LinuxExtras.prepareWindow(OsKind.LINUX, wayland);
            hints.verify(() -> SDLHints.SDL_SetHint(SDLHints.SDL_HINT_VIDEO_DISPLAY_PRIORITY, "HDMI-A-1,DP-2,DP-1"));
        }
    }

    @Test
    void aCompositorWithoutAnOrderLeavesSdlDefaultsAlone() {
        try (MockedStatic<SDLHints> hints = Mockito.mockStatic(SDLHints.class);
             MockedStatic<WaylandOutputOrder> order = Mockito.mockStatic(WaylandOutputOrder.class)) {
            order.when(WaylandOutputOrder::outputNames).thenReturn(List.of());
            LinuxExtras.prepareWindow(OsKind.LINUX, Map.of("XDG_SESSION_TYPE", "wayland", "WAYLAND_SOCKET", "5"));
            hints.verifyNoInteractions();
        }
        LinuxExtras.options().waylandFullscreenMonitor = false;
        LinuxExtras.prepareWindow();
    }
}
