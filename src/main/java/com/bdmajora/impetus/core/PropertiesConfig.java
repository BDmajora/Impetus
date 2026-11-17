package com.bdmajora.impetus.core;

import net.minecraft.launchwrapper.Launch;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        save(values, Map.of(), Map.of());
    }

    // Writes values in order with each key's note and default above it and does nothing before the first load.
    public void save(Map<String, String> values, Map<String, String> notes, Map<String, String> defaults) {
        if (this.file == null) {
            return;
        }
        Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.ISO_8859_1)) {
                writer.write(format(values, notes, defaults));
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

    // Returns the file text with the header and each key under its note and default.
    String format(Map<String, String> values, Map<String, String> notes, Map<String, String> defaults) {
        StringBuilder out = new StringBuilder();
        comment(out, this.header);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String note = notes.get(key);
            String fallback = defaults.get(key);
            if (out.length() > 0) {
                out.append('\n');
            }
            comment(out, note);
            if (fallback != null) {
                comment(out, "Default: " + fallback);
            }
            escape(out, key, true);
            out.append('=');
            escape(out, entry.getValue(), false);
            out.append('\n');
        }
        return out.toString();
    }

    // Appends the text as comment lines of at most 78 characters.
    private static void comment(StringBuilder out, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder("#");
            for (String word : paragraph.split(" ")) {
                if (word.isEmpty()) {
                    continue;
                }
                if (line.length() > 1 && line.length() + 1 + word.length() > 78) {
                    escapeComment(out, line);
                    line.setLength(1);
                }
                line.append(' ').append(word);
            }
            escapeComment(out, line);
        }
    }

    // Appends one comment line with characters outside Latin-1 written as escapes.
    private static void escapeComment(StringBuilder out, CharSequence line) {
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c > 0xFF) {
                unicode(out, c);
            } else {
                out.append(c);
            }
        }
        out.append('\n');
    }

    // Appends a key or value escaped the way Properties.load reads it back.
    private static void escape(StringBuilder out, String text, boolean key) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\t' -> out.append("\\t");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\f' -> out.append("\\f");
                case '=', ':', '#', '!' -> {
                    if (key) {
                        out.append('\\');
                    }
                    out.append(c);
                }
                case ' ' -> {
                    if (key || i == 0) {
                        out.append('\\');
                    }
                    out.append(c);
                }
                default -> {
                    if (c < 0x20 || c > 0x7E) {
                        unicode(out, c);
                    } else {
                        out.append(c);
                    }
                }
            }
        }
    }

    // Appends the character as a backslash u escape.
    private static void unicode(StringBuilder out, char c) {
        out.append("\\u");
        String hex = Integer.toHexString(c).toUpperCase(Locale.ROOT);
        for (int i = hex.length(); i < 4; i++) {
            out.append('0');
        }
        out.append(hex);
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
