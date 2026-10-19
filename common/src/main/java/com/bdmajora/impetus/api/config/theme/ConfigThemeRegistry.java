package com.bdmajora.impetus.api.config.theme;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfigThemeRegistry {
    private static final Map<String, ConfigTheme> THEMES = new ConcurrentHashMap<>();

    private ConfigThemeRegistry() {
    }

    // Later registrations replace earlier; the theme's id is already canonical, so it is the key as is
    public static void register(ConfigTheme theme) {
        Objects.requireNonNull(theme, "Theme must not be null");
        THEMES.put(theme.modId(), theme);
    }

    // Empty when the mod registered no theme; the id may be spelled in any case
    public static OptionalInt getAccentColor(String modId) {
        // No theme can have a null or blank id, and ConcurrentHashMap throws on a null key
        if (modId == null || modId.isBlank()) {
            return OptionalInt.empty();
        }
        ConfigTheme theme = THEMES.get(ConfigTheme.canonicalModId(modId));
        return theme != null ? OptionalInt.of(theme.accentColor()) : OptionalInt.empty();
    }
}
