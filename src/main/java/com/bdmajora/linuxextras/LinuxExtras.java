package com.bdmajora.linuxextras;

import com.bdmajora.impetus.engine.impl.compat.environment.OsKind;
import com.bdmajora.linuxextras.wayland.WaylandSession;
import net.minecraftforge.common.ForgeEarlyConfig;
import net.minecraftforge.common.config.ConfigManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

// Linux desktop fixes Cleanroom leaves out, starting with a native Wayland window that goes fullscreen on the primary monitor
public final class LinuxExtras {
    public static final Logger LOGGER = LogManager.getLogger("Impetus/LinuxExtras");

    // Set when Impetus, not the user, turned Cleanroom's Wayland switch on, so it is handed back once the window exists
    private static boolean borrowedWaylandSwitch;

    private LinuxExtras() {
    }

    public static LinuxExtrasConfig options() {
        return LinuxExtrasConfig.get();
    }

    // From the coremod constructor, before anything starts GLFW: Cleanroom sends a Wayland session through XWayland unless forge_early.cfg's FORCE_WAYLAND is on, and Sys reads that once when it initialises GLFW
    public static void chooseWindowSystem() {
        chooseWindowSystem(OsKind.current(), System.getenv());
    }

    static void chooseWindowSystem(OsKind os, Map<String, String> env) {
        if (os != OsKind.LINUX || ForgeEarlyConfig.FORCE_WAYLAND || !options().forceWayland || !WaylandSession.available(env)) {
            return;
        }
        ForgeEarlyConfig.FORCE_WAYLAND = true;
        borrowedWaylandSwitch = true;
        LOGGER.info("Running the game window natively on Wayland");
    }

    // After Cleanroom's window exists: Display.create wrote every early option to forge_early.cfg, the borrowed switch included, so the user's own value goes back into the file
    public static void onWindowCreated() {
        if (borrowedWaylandSwitch) {
            borrowedWaylandSwitch = false;
            ForgeEarlyConfig.FORCE_WAYLAND = false;
            ConfigManager.sync(ForgeEarlyConfig.class);
        }
    }
}
