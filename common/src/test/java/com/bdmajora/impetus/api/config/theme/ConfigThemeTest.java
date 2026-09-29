package com.bdmajora.impetus.api.config.theme;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class ConfigThemeTest {
    @Test
    void builderDefaultsAndRegistryLookup() {
        ConfigTheme theme = ConfigTheme.builder("themed").build();
        assertEquals("themed", theme.getModId());
        assertEquals(0xFF00CBCB, theme.getAccentColor());
        ConfigTheme custom = ConfigTheme.builder("themed").accentColor(0xFF123456).build();
        ConfigThemeRegistry.register(theme);
        ConfigThemeRegistry.register(custom);
        assertEquals(OptionalInt.of(0xFF123456), ConfigThemeRegistry.getAccentColor("themed"));
        assertEquals(OptionalInt.empty(), ConfigThemeRegistry.getAccentColor("unknown"));
        assertThrows(NullPointerException.class, () -> ConfigTheme.builder(null));
    }
}
