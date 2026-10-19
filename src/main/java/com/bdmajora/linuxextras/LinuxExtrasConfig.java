package com.bdmajora.linuxextras;

import com.bdmajora.impetus.core.PropertiesConfig;

import java.util.Map;

// Linux Extras' switches, read before SDL video initialization
public final class LinuxExtrasConfig {
    private static final String FILE_NAME = "impetus-linuxextras.cfg";

    private static LinuxExtrasConfig instance;

    // The backing file; set by load before the instance is published
    private PropertiesConfig file;

    // Starts SDL on the preferred primary monitor on Wayland
    public boolean waylandFullscreenMonitor;
    // Wayland output names (DP-1, HDMI-A-1) to treat as primary, first connected one wins; empty asks the compositor, which only KDE answers
    public String[] waylandMonitors;
    // Opens links and folders with xdg-open, since AWT's Desktop API is unsupported on most Linux desktops and links could only be copied
    public boolean xdgOpen;

    private LinuxExtrasConfig(PropertiesConfig props) {
        this.waylandFullscreenMonitor = props.bool("waylandFullscreenMonitor", true);
        this.waylandMonitors = props.list("waylandMonitors", "");
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
        values.put("waylandFullscreenMonitor", Boolean.toString(this.waylandFullscreenMonitor));
        values.put("waylandMonitors", String.join(",", this.waylandMonitors));
        values.put("xdgOpen", Boolean.toString(this.xdgOpen));
        return values;
    }
}
