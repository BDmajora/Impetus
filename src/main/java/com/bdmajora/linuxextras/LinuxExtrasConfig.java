package com.bdmajora.linuxextras;

import com.bdmajora.impetus.core.PropertiesConfig;

import java.util.Map;

// Linux Extras' switches, a plain Properties file (see PropertiesConfig) since the window system is chosen in the coremod constructor, before Forge or Minecraft classes are safe
public final class LinuxExtrasConfig {
    private static final String FILE_NAME = "impetus-linuxextras.cfg";

    private static LinuxExtrasConfig instance;

    // The backing file; set by load before the instance is published
    private PropertiesConfig file;

    // Runs the window natively on a Wayland session, where Cleanroom otherwise goes through XWayland; read once per launch
    public boolean forceWayland;
    // Sends fullscreen and borderless to the primary monitor on Wayland, where Cleanroom's monitor-under-the-window rule always lands on the output at the desktop's origin
    public boolean waylandFullscreenMonitor;
    // Wayland output names (DP-1, HDMI-A-1) to treat as primary, first connected one wins; empty asks the compositor, which only KDE answers
    public String[] waylandMonitors;
    // Sets the window icon through xdg-toplevel-icon-v1, since GLFW's Wayland backend cannot set one
    public boolean waylandWindowIcon;
    // Opens links and folders with xdg-open, since AWT's Desktop API is unsupported on most Linux desktops and links could only be copied
    public boolean xdgOpen;

    private LinuxExtrasConfig(PropertiesConfig props) {
        this.forceWayland = props.bool("forceWayland", true);
        this.waylandFullscreenMonitor = props.bool("waylandFullscreenMonitor", true);
        this.waylandMonitors = props.list("waylandMonitors", "");
        this.waylandWindowIcon = props.bool("waylandWindowIcon", true);
        this.xdgOpen = props.bool("xdgOpen", true);
    }

    // Loads on first use and caches; every reader shares the one instance
    public static LinuxExtrasConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    // Reads the file if present, otherwise starts from defaults and writes them out
    private static LinuxExtrasConfig load() {
        PropertiesConfig props = new PropertiesConfig(LinuxExtras.LOGGER, FILE_NAME, "Impetus / Linux Extras. Delete a line to restore its default.");
        props.load();
        LinuxExtrasConfig config = new LinuxExtrasConfig(props);
        config.file = props;
        config.save();
        return config;
    }

    public void save() {
        this.file.save(values());
    }

    // Every key in declaration order, so a rewritten file lists every switch
    private Map<String, String> values() {
        Map<String, String> values = PropertiesConfig.values();
        values.put("forceWayland", Boolean.toString(this.forceWayland));
        values.put("waylandFullscreenMonitor", Boolean.toString(this.waylandFullscreenMonitor));
        values.put("waylandMonitors", String.join(",", this.waylandMonitors));
        values.put("waylandWindowIcon", Boolean.toString(this.waylandWindowIcon));
        values.put("xdgOpen", Boolean.toString(this.xdgOpen));
        return values;
    }
}
