package com.bdmajora.impetus.booter.util;

import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

// The plain-Properties config file the mixin plugins read during coremod setup, before Forge/Minecraft classes are safe; Launch is the only outside class referenced, so every module config that must load that early shares this rather than its own copy of the file handling
public final class PropertiesConfig {
    private final Logger logger;
    private final String fileName;
    private final String header;
    // Where save writes; null only if the config directory could not be resolved
    private Path file;
    private Properties loaded = new Properties();

    public PropertiesConfig(Logger logger, String fileName, String header) {
        this.logger = logger;
        this.fileName = fileName;
        this.header = header;
    }

    // Reads the file if present, otherwise starts from defaults; the caller parses the result and then writes back so a fresh file discovers every switch
    public Properties load() {
        this.file = configDirectory().resolve(this.fileName);
        Properties props = new Properties();
        if (Files.isRegularFile(this.file)) {
            try (InputStream in = Files.newInputStream(this.file)) {
                props.load(in);
            } catch (IOException e) {
                this.logger.error("Could not read {}, falling back to defaults", this.file, e);
            }
        }
        this.loaded = props;
        return props;
    }

    // Rewrites the file with every key so a user who never opened it still discovers the switches; user-set values are preserved verbatim by the caller re-supplying them
    public void save(Map<String, String> values) {
        if (this.file == null) {
            return;
        }
        Properties out = new Properties();
        out.putAll(values);
        try (OutputStream stream = Files.newOutputStream(this.file)) {
            out.store(stream, this.header);
        } catch (IOException e) {
            this.logger.warn("Could not write {}", this.file, e);
        }
    }

    // Insertion-ordered so the written file lists switches in declaration order
    public static Map<String, String> values() {
        return new LinkedHashMap<>();
    }

    // The config directory, created eagerly; falls back to the working directory when minecraftHome is unset
    private Path configDirectory() {
        return configDirectory(this.logger).toPath();
    }

    // config/ under the game directory, created eagerly; the modules on Forge's Configuration resolve their file through this too
    public static File configDirectory(Logger logger) {
        File home = Launch.minecraftHome;
        File directory = new File(home == null ? new File(".") : home, "config");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            logger.warn("Could not create {}, configuration will not persist", directory);
        }
        return directory;
    }

    public static File configFile(Logger logger, String fileName) {
        return new File(configDirectory(logger), fileName);
    }

    // Lenient boolean parse; anything unrecognised keeps the default
    public boolean bool(String key, boolean fallback) {
        String value = this.loaded.getProperty(key);
        if (value == null) {
            return fallback;
        }
        value = value.trim();
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                ? Boolean.parseBoolean(value)
                : fallback;
    }

    // Clamped integer parse; out-of-range and unparseable values keep the default
    public int integer(String key, int fallback, int min, int max) {
        String value = this.loaded.getProperty(key);
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed < min || parsed > max ? fallback : parsed;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // Comma-separated list; blank entries are dropped so a trailing comma is harmless
    public String[] list(String key, String fallback) {
        String value = this.loaded.getProperty(key);
        if (value == null) {
            value = fallback;
        }
        List<String> entries = new ArrayList<>();
        for (String entry : value.split(",")) {
            entry = entry.trim();
            if (!entry.isEmpty()) {
                entries.add(entry);
            }
        }
        return entries.toArray(new String[0]);
    }
}
