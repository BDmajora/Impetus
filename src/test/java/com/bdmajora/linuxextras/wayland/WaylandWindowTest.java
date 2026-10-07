package com.bdmajora.linuxextras.wayland;

import com.bdmajora.linuxextras.LinuxExtras;
import com.bdmajora.linuxextras.LinuxExtrasConfig;
import com.bdmajora.testing.Mixins;
import com.bdmajora.testing.Statics;
import net.minecraft.launchwrapper.Launch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWayland;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.Display;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class WaylandWindowTest {
    private static final long WINDOW = 0xCAFEL;
    private static final long DISPLAY = 0xD15L;
    private static final long LEFT = 0x100L;
    private static final long MAIN = 0x200L;
    private static final long RIGHT = 0x300L;

    @TempDir
    Path dir;
    private File realHome;
    private MockedStatic<GLFW> glfw;

    @BeforeEach
    void desktop() {
        this.realHome = Launch.minecraftHome;
        Statics.set(Launch.class, "minecraftHome", this.dir.toFile());
        Mixins.set(LinuxExtrasConfig.class, "instance", null);
        this.glfw = Mockito.mockStatic(GLFW.class);
        // Three outputs left to right; GLFW's first is the leftmost, as KWin announces them
        this.glfw.when(GLFW::glfwGetMonitors).thenReturn(PointerBuffer.allocateDirect(3).put(0, LEFT).put(1, MAIN).put(2, RIGHT));
        this.glfw.when(() -> GLFW.glfwGetMonitorName(LEFT)).thenReturn("HDMI-A-1");
        this.glfw.when(() -> GLFW.glfwGetMonitorName(MAIN)).thenReturn("DP-1");
        this.glfw.when(() -> GLFW.glfwGetMonitorName(RIGHT)).thenReturn("DP-2");
        this.glfw.when(GLFW::glfwGetPrimaryMonitor).thenReturn(LEFT);
    }

    @AfterEach
    void restore() {
        this.glfw.close();
        Mixins.set(WaylandWindow.class, "heldIcons", null);
        Statics.set(Launch.class, "minecraftHome", this.realHome);
        Mixins.set(LinuxExtrasConfig.class, "instance", null);
    }

    private void platform(int platform) {
        this.glfw.when(GLFW::glfwGetPlatform).thenReturn(platform);
    }

    @Test
    void onlyAWaylandGlfwCounts() {
        platform(GLFW.GLFW_PLATFORM_X11);
        assertFalse(WaylandWindow.active());
        platform(GLFW.GLFW_PLATFORM_WAYLAND);
        assertTrue(WaylandWindow.active());
    }

    @Test
    void fullscreenGoesToThePrimaryOutputInsteadOfTheLeftmost() {
        try (MockedStatic<GLFWNativeWayland> wayland = Mockito.mockStatic(GLFWNativeWayland.class);
             MockedStatic<WaylandOutputOrder> order = Mockito.mockStatic(WaylandOutputOrder.class)) {
            wayland.when(GLFWNativeWayland::glfwGetWaylandDisplay).thenReturn(DISPLAY);
            order.when(() -> WaylandOutputOrder.outputNames(DISPLAY)).thenReturn(List.of("DP-9", "DP-1", "HDMI-A-1"));

            // On X11 Cleanroom's own choice stands, since the window position it reads is real there
            platform(GLFW.GLFW_PLATFORM_X11);
            assertEquals(0L, WaylandWindow.fullscreenMonitor());

            // KDE's order, past an output that is not connected
            platform(GLFW.GLFW_PLATFORM_WAYLAND);
            assertEquals(MAIN, WaylandWindow.fullscreenMonitor());

            // Outputs the user named come first, an unplugged one passed over
            LinuxExtrasConfig options = LinuxExtras.options();
            options.waylandMonitors = new String[] {"DP-7", "DP-2"};
            assertEquals(RIGHT, WaylandWindow.fullscreenMonitor());

            // Nothing to go on leaves GLFW's first, as does a GLFW with no monitor list
            options.waylandMonitors = new String[0];
            order.when(() -> WaylandOutputOrder.outputNames(DISPLAY)).thenReturn(List.of());
            assertEquals(LEFT, WaylandWindow.fullscreenMonitor());
            this.glfw.when(GLFW::glfwGetMonitors).thenReturn(null);
            assertEquals(LEFT, WaylandWindow.fullscreenMonitor());

            // The switch off leaves Cleanroom's choice even on Wayland
            options.waylandFullscreenMonitor = false;
            assertEquals(0L, WaylandWindow.fullscreenMonitor());
        }
    }

    @Test
    void aFullscreenWindowOnTheWrongOutputMovesToThePrimary() {
        GLFWVidMode mode = Mockito.mock(GLFWVidMode.class);
        Mockito.when(mode.width()).thenReturn(2560);
        Mockito.when(mode.height()).thenReturn(1440);
        Mockito.when(mode.refreshRate()).thenReturn(144);
        try (MockedStatic<GLFWNativeWayland> wayland = Mockito.mockStatic(GLFWNativeWayland.class);
             MockedStatic<WaylandOutputOrder> order = Mockito.mockStatic(WaylandOutputOrder.class)) {
            wayland.when(GLFWNativeWayland::glfwGetWaylandDisplay).thenReturn(DISPLAY);
            order.when(() -> WaylandOutputOrder.outputNames(DISPLAY)).thenReturn(List.of("DP-1"));
            platform(GLFW.GLFW_PLATFORM_WAYLAND);

            // No window, a window out of fullscreen, and one already on the primary all stay put
            assertNull(WaylandWindow.moveToFullscreenMonitor(0L));
            assertNull(WaylandWindow.moveToFullscreenMonitor(WINDOW));
            this.glfw.when(() -> GLFW.glfwGetWindowMonitor(WINDOW)).thenReturn(MAIN);
            assertNull(WaylandWindow.moveToFullscreenMonitor(WINDOW));

            // Cleanroom's pick, the leftmost output: with no mode for the primary it stays, otherwise it moves there at the primary's mode
            this.glfw.when(() -> GLFW.glfwGetWindowMonitor(WINDOW)).thenReturn(LEFT);
            assertNull(WaylandWindow.moveToFullscreenMonitor(WINDOW));
            this.glfw.verify(() -> GLFW.glfwSetWindowMonitor(anyLong(), anyLong(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt()), never());
            this.glfw.when(() -> GLFW.glfwGetVideoMode(MAIN)).thenReturn(mode);
            assertSame(mode, WaylandWindow.moveToFullscreenMonitor(WINDOW));
            this.glfw.verify(() -> GLFW.glfwSetWindowMonitor(WINDOW, MAIN, 0, 0, 2560, 1440, 144));

            // On X11 Cleanroom's pick follows the real window position
            platform(GLFW.GLFW_PLATFORM_X11);
            assertNull(WaylandWindow.moveToFullscreenMonitor(WINDOW));
        }
    }

    @Test
    void waylandIconsWaitForTheWindowAndGoThroughTheProtocol() {
        ByteBuffer[] icons = {ByteBuffer.allocate(4)};
        ByteBuffer[] later = {ByteBuffer.allocate(16)};
        // Kept off the monitor path, which onWindowCreated also takes
        LinuxExtras.options().waylandFullscreenMonitor = false;
        try (MockedStatic<WaylandWindowIcon> icon = Mockito.mockStatic(WaylandWindowIcon.class)) {
            // X11, or the switch off, leaves them to Cleanroom
            platform(GLFW.GLFW_PLATFORM_X11);
            assertFalse(WaylandWindow.takeIcon(icons));
            platform(GLFW.GLFW_PLATFORM_WAYLAND);
            LinuxExtras.options().waylandWindowIcon = false;
            assertFalse(WaylandWindow.takeIcon(icons));
            LinuxExtras.options().waylandWindowIcon = true;

            // Offered before the window exists, they wait for it, and a missing window keeps them waiting
            Display.created = false;
            assertTrue(WaylandWindow.takeIcon(icons));
            WaylandWindow.onWindowCreated(0L);
            icon.verify(() -> WaylandWindowIcon.setIcon(anyLong(), any()), never());
            WaylandWindow.onWindowCreated(WINDOW);
            WaylandWindow.onWindowCreated(WINDOW);
            icon.verify(() -> WaylandWindowIcon.setIcon(WINDOW, icons), times(1));

            // Once it exists they go straight to it
            Display.created = true;
            Display.window = WINDOW;
            assertTrue(WaylandWindow.takeIcon(later));
            icon.verify(() -> WaylandWindowIcon.setIcon(WINDOW, later));
        }
    }
}
