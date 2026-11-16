package com.bdmajora.linuxextras;

import com.bdmajora.impetus.core.PropertiesConfig;

import java.util.Map;

// Settings of the Linux Extras module.
public final class LinuxExtrasConfig {
    // Name of the config file.
    private static final String FILE_NAME = "impetus-linuxextras.cfg";

    // Loaded config or null before the first read.
    private static LinuxExtrasConfig instance;

    // Properties file of the config.
    private PropertiesConfig file;

    // Flag that picks the fullscreen monitor.
    public boolean waylandFullscreenMonitor;
    // Monitor names in order.
    public String[] waylandMonitors;
    // Flag that opens links with the desktop handler.
    public boolean xdgOpen;

    // Creates a linux extras config from its props.
    private LinuxExtrasConfig(PropertiesConfig props) {
        this.waylandFullscreenMonitor = props.bool("waylandFullscreenMonitor", true);
        this.waylandMonitors = props.list("waylandMonitors", "");
        this.xdgOpen = props.bool("xdgOpen", true);
    }

    // Returns the value.
    public static LinuxExtrasConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    // Reads the config file and fills missing values with defaults.
    private static LinuxExtrasConfig load() {
        PropertiesConfig props = new PropertiesConfig(LinuxExtras.LOGGER, FILE_NAME, "Impetus / Linux Extras. Delete a line to restore its default.");
        props.load();
        LinuxExtrasConfig config = new LinuxExtrasConfig(props);
        config.file = props;
        config.save();
        return config;
    }

    // Writes every value to the config file.
    public void save() {
        this.file.save(values());
    }

    // Returns every setting as text by key.
    private Map<String, String> values() {
        Map<String, String> values = PropertiesConfig.values();
        values.put("waylandFullscreenMonitor", Boolean.toString(this.waylandFullscreenMonitor));
        values.put("waylandMonitors", String.join(",", this.waylandMonitors));
        values.put("xdgOpen", Boolean.toString(this.xdgOpen));
        return values;
    }
}
