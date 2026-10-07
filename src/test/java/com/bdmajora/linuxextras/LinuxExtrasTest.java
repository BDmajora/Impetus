package com.bdmajora.linuxextras;

import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.ForgeEarlyConfig;
import net.minecraftforge.common.config.ConfigManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;

class LinuxExtrasTest {
    @TempDir
    Path dir;
    private File realHome;
    private final boolean realWayland = ForgeEarlyConfig.FORCE_WAYLAND;

    @BeforeEach
    void freshConfig() {
        this.realHome = Launch.minecraftHome;
        Statics.set(Launch.class, "minecraftHome", this.dir.toFile());
        Mixins.set(LinuxExtrasConfig.class, "instance", null);
        ForgeEarlyConfig.FORCE_WAYLAND = false;
    }

    @AfterEach
    void restore() {
        Statics.set(Launch.class, "minecraftHome", this.realHome);
        Mixins.set(LinuxExtrasConfig.class, "instance", null);
        Mixins.set(LinuxExtras.class, "borrowedWaylandSwitch", false);
        ForgeEarlyConfig.FORCE_WAYLAND = this.realWayland;
    }

    private Properties written() throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(this.dir.resolve("config/impetus-linuxextras.cfg"))) {
            props.load(in);
        }
        return props;
    }

    @Test
    void theConfigWritesEverySwitchAndReadsEditsBack() throws IOException {
        LinuxExtrasConfig defaults = LinuxExtras.options();
        assertSame(defaults, LinuxExtras.options());
        assertTrue(defaults.forceWayland);
        assertTrue(defaults.waylandFullscreenMonitor);
        assertArrayEquals(new String[0], defaults.waylandMonitors);
        assertTrue(defaults.waylandWindowIcon);
        assertTrue(defaults.xdgOpen);
        assertEquals("", written().getProperty("waylandMonitors"));

        Files.writeString(this.dir.resolve("config/impetus-linuxextras.cfg"), "forceWayland=false\nwaylandMonitors=DP-2, HDMI-A-1\nwaylandWindowIcon=false\nxdgOpen=false\n");
        Mixins.set(LinuxExtrasConfig.class, "instance", null);
        LinuxExtrasConfig edited = LinuxExtras.options();
        assertFalse(edited.forceWayland);
        assertTrue(edited.waylandFullscreenMonitor);
        assertArrayEquals(new String[] {"DP-2", "HDMI-A-1"}, edited.waylandMonitors);
        assertFalse(edited.waylandWindowIcon);
        assertFalse(edited.xdgOpen);
        // The rewrite fills in the switch the user left out
        assertEquals("true", written().getProperty("waylandFullscreenMonitor"));
        assertEquals("DP-2,HDMI-A-1", written().getProperty("waylandMonitors"));
    }

    @Test
    void waylandIsBorrowedOnlyOnAReachableWaylandSessionAndHandedBack() throws IOException {
        Files.createFile(this.dir.resolve("wayland-1"));
        Map<String, String> wayland = Map.of("XDG_SESSION_TYPE", "wayland", "XDG_RUNTIME_DIR", this.dir.toString(), "WAYLAND_DISPLAY", "wayland-1");
        try (MockedStatic<ConfigManager> configs = Mockito.mockStatic(ConfigManager.class)) {
            // Another system, an X11 session, the option off, and a Wayland session whose socket is gone all stay on X11
            LinuxExtras.chooseWindowSystem(OsKind.WINDOWS, wayland);
            LinuxExtras.chooseWindowSystem(OsKind.LINUX, Map.of("XDG_SESSION_TYPE", "x11"));
            LinuxExtras.options().forceWayland = false;
            LinuxExtras.chooseWindowSystem(OsKind.LINUX, wayland);
            LinuxExtras.options().forceWayland = true;
            LinuxExtras.chooseWindowSystem(OsKind.LINUX, Map.of("XDG_SESSION_TYPE", "wayland", "XDG_RUNTIME_DIR", this.dir.toString(), "WAYLAND_DISPLAY", "wayland-9"));
            assertFalse(ForgeEarlyConfig.FORCE_WAYLAND);
            LinuxExtras.onWindowCreated();
            configs.verifyNoInteractions();

            LinuxExtras.chooseWindowSystem(OsKind.LINUX, wayland);
            assertTrue(ForgeEarlyConfig.FORCE_WAYLAND);
            // Once the window exists the switch goes back and forge_early.cfg is rewritten with it, once
            LinuxExtras.onWindowCreated();
            assertFalse(ForgeEarlyConfig.FORCE_WAYLAND);
            LinuxExtras.onWindowCreated();
            configs.verify(() -> ConfigManager.sync(ForgeEarlyConfig.class), times(1));

            // A user who turned Cleanroom's switch on keeps it
            ForgeEarlyConfig.FORCE_WAYLAND = true;
            LinuxExtras.chooseWindowSystem(OsKind.LINUX, wayland);
            LinuxExtras.onWindowCreated();
            assertTrue(ForgeEarlyConfig.FORCE_WAYLAND);
            configs.verify(() -> ConfigManager.sync(ForgeEarlyConfig.class), times(1));
        }
    }

    @Test
    void theCoremodAsksAboutThisMachine() {
        // With the option off the real environment changes nothing, whatever session the tests run in
        LinuxExtras.options().forceWayland = false;
        LinuxExtras.chooseWindowSystem();
        assertFalse(ForgeEarlyConfig.FORCE_WAYLAND);
    }
}
