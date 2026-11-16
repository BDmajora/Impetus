package com.bdmajora.impetus.core;

import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

// Properties file in the config directory with typed reads and atomic saves.
public final class PropertiesConfig {
    // Logger of read and write failures.
    private final Logger logger;
    // Name of the file.
    private final String fileName;
    // Comment written at the top of the file.
    private final String header;
    // Path of the file or null before the first load.
    private Path file;
    // Values of the last load.
    private Properties loaded = new Properties();

    // Creates a config for a file name.
    public PropertiesConfig(Logger logger, String fileName, String header) {
        this.logger = logger;
        this.fileName = fileName;
        this.header = header;
    }

    // Reads the file and returns its values or empty values when it is missing or unreadable.
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

    // Writes values through a temporary file and does nothing before the first load.
    public void save(Map<String, String> values) {
        if (this.file == null) {
            return;
        }
        Properties out = new Properties();
        out.putAll(values);
        Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
        try {
            try (OutputStream stream = Files.newOutputStream(temp)) {
                out.store(stream, this.header);
            }
            try {
                Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            this.logger.warn("Could not write {}", this.file, e);
            // Removes the temporary file of a failed write
            try {
                Files.deleteIfExists(temp);
            } catch (IOException cleanup) {
                e.addSuppressed(cleanup);
            }
        }
    }

    // Returns an empty ordered map of values.
    public static Map<String, String> values() {
        return new LinkedHashMap<>();
    }

    // Returns the config directory as a path.
    private Path configDirectory() {
        return configDirectory(this.logger).toPath();
    }

    // Returns the config directory and creates it when missing.
    public static File configDirectory(Logger logger) {
        File home = Launch.minecraftHome;
        File directory = new File(home == null ? new File(".") : home, "config");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            logger.warn("Could not create {}, configuration will not persist", directory);
        }
        return directory;
    }

    // Returns a file in the config directory.
    public static File configFile(Logger logger, String fileName) {
        return new File(configDirectory(logger), fileName);
    }

    // Returns a boolean value or the fallback for a missing or malformed value.
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

    // Returns an int value or the fallback for a missing or malformed or out of range value.
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

    // Returns the non empty comma separated entries of a value or of the fallback.
    public String[] list(String key, String fallback) {
        String value = this.loaded.getProperty(key);
        if (value == null) {
            value = fallback == null ? "" : fallback;
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
