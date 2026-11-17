package com.bdmajora.impetus.umbra.gui;

import com.bdmajora.impetus.umbra.shaderpack.include.AbsolutePackPath;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// Translations of the active shader pack.
public final class PackLanguage {
    private final Map<String, String> entries = new HashMap<>();

    // Creates a pack language from its sources and locale.
    public PackLanguage(Map<AbsolutePackPath, String> sources, String locale) {
        loadLangFile(sources, "en_us");
        String normalized = locale == null ? "en_us" : locale.toLowerCase(Locale.ROOT);
        if (!normalized.equals("en_us")) {
            loadLangFile(sources, normalized);
        }
    }

    // Reads the lang file of a locale.
    private void loadLangFile(Map<AbsolutePackPath, String> sources, String localeName) {
        String contents = findLangFile(sources, localeName);
        if (contents == null) {
            return;
        }
        for (String rawLine : contents.split("\r\n|\r|\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = translateColorCodes(line.substring(eq + 1).trim());
            if (!key.isEmpty()) {
                this.entries.put(key, value);
            }
        }
    }

    // Returns the lang file text of a locale or null.
    private static String findLangFile(Map<AbsolutePackPath, String> sources, String localeName) {
        String target = "/lang/" + localeName + ".lang";
        for (Map.Entry<AbsolutePackPath, String> entry : sources.entrySet()) {
            if (entry.getKey().getPathString().equalsIgnoreCase(target)) {
                return entry.getValue();
            }
        }
        return null;
    }

    // Replaces ampersand color codes with section signs.
    private static String translateColorCodes(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '&' && i + 1 < value.length() && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(value.charAt(i + 1)) >= 0) {
                sb.append('§');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // Returns the value.
    private String get(String key) {
        return this.entries.get(key);
    }

    // Returns the label of an option or a fallback.
    public String optionLabel(String name, String fallback) {
        String value = get("option." + name);
        if (value != null) {
            return value;
        }
        return fallback != null ? fallback : name;
    }

    // Returns the translated label of one option value or the raw value.
    public String valueLabel(String name, String value) {
        String label = get("value." + name + "." + value);
        return label != null ? label : value;
    }

    // Returns the value prefix of an option.
    public String prefix(String name) {
        String value = get("prefix." + name);
        return value != null ? value : "";
    }

    // Returns the value suffix of an option.
    public String suffix(String name) {
        String value = get("suffix." + name);
        return value != null ? value : "";
    }

    // Returns the label of a screen.
    public String screenLabel(String name) {
        String value = get("screen." + name);
        return value != null ? value : name;
    }

    // Returns the comment of an option.
    public String comment(String name) {
        return get("option." + name + ".comment");
    }

    // Returns the label of a profile.
    public String profileLabel(String name) {
        String value = get("profile." + name);
        return value != null ? value : name;
    }
}
