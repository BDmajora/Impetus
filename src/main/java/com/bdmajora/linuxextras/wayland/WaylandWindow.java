package com.bdmajora.linuxextras.wayland;

import com.bdmajora.impetus.impl.platform.GameWindow;
import com.bdmajora.linuxextras.LinuxExtras;
import com.bdmajora.linuxextras.LinuxExtrasConfig;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWayland;
import org.lwjgl.glfw.GLFWVidMode;

import java.nio.ByteBuffer;

// Cleanroom's window decisions that Wayland breaks, put right after Cleanroom makes them; off Wayland nothing here acts, since the window position Cleanroom goes by is real there
public final class WaylandWindow {
    // Icons offered before the window existed, which xdg-toplevel-icon-v1 needs
    private static ByteBuffer[] heldIcons;

    private WaylandWindow() {
    }

    // Whether GLFW runs on Wayland; only asked once Cleanroom's Display has started GLFW
    public static boolean active() {
        return GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND;
    }

    // The monitor fullscreen belongs on, or 0 to keep Cleanroom's choice: a Wayland window's position always reads 0,0, so Cleanroom's monitor-under-the-window rule always picks the output at the desktop's origin
    public static long fullscreenMonitor() {
        LinuxExtrasConfig options = LinuxExtras.options();
        return options.waylandFullscreenMonitor && active() ? primaryMonitor(options.waylandMonitors) : 0L;
    }

    // The first connected output the user named, else the first KDE lists as its order (the primary), else GLFW's first; GLFW's Wayland backend has no primary of its own
    static long primaryMonitor(String[] preferred) {
        PointerBuffer monitors = GLFW.glfwGetMonitors();
        if (monitors != null) {
            for (String name : preferred) {
                long monitor = find(monitors, name);
                if (monitor != 0L) {
                    return monitor;
                }
            }
            for (String name : WaylandOutputOrder.outputNames(GLFWNativeWayland.glfwGetWaylandDisplay())) {
                long monitor = find(monitors, name);
                if (monitor != 0L) {
                    return monitor;
                }
            }
        }
        return GLFW.glfwGetPrimaryMonitor();
    }

    private static long find(PointerBuffer monitors, String name) {
        for (int i = 0; i < monitors.limit(); i++) {
            if (name.equals(GLFW.glfwGetMonitorName(monitors.get(i)))) {
                return monitors.get(i);
            }
        }
        return 0L;
    }

    // Moves a fullscreen window Cleanroom put on another output to fullscreenMonitor(), answering the new mode, or null when nothing moved; Wayland applies both requests at the window's next frame, so it never shows on the wrong output
    public static GLFWVidMode moveToFullscreenMonitor(long window) {
        long monitor = window == 0L ? 0L : fullscreenMonitor();
        long current = monitor == 0L ? 0L : GLFW.glfwGetWindowMonitor(window);
        if (current == 0L || current == monitor) {
            return null;
        }
        GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
        if (mode != null) {
            GLFW.glfwSetWindowMonitor(window, monitor, 0, 0, mode.width(), mode.height(), mode.refreshRate());
        }
        return mode;
    }

    // Takes the icons over on Wayland, where Cleanroom's glfwSetWindowIcon only reports an error; false leaves them to Cleanroom
    public static boolean takeIcon(ByteBuffer[] icons) {
        // Asking for the handle starts Cleanroom's Display, and with it GLFW, so the platform is known
        long window = GameWindow.handle();
        if (!LinuxExtras.options().waylandWindowIcon || !active()) {
            return false;
        }
        if (window == 0L) {
            heldIcons = icons;
        } else {
            WaylandWindowIcon.setIcon(window, icons);
        }
        return true;
    }

    // The new window gets the held icons, and leaves the output Cleanroom chose if the game starts fullscreen
    public static void onWindowCreated(long window) {
        if (heldIcons != null && window != 0L) {
            WaylandWindowIcon.setIcon(window, heldIcons);
            heldIcons = null;
        }
        moveToFullscreenMonitor(window);
    }
}
