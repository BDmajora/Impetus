package com.bdmajora.impetus.engine.impl.gui.theme;

import com.bdmajora.impetus.api.config.theme.ConfigTheme;
import com.bdmajora.impetus.api.config.theme.ConfigThemeRegistry;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DefaultColorsTest {
    // Registered themes are global, so they are dropped after every test that adds one
    @AfterEach
    void clearRegistry() {
        Statics.<Map<String, ConfigTheme>>get(ConfigThemeRegistry.class, "THEMES").clear();
    }

    @Test
    void knownModsHaveFixedAccentsAndOthersHashIntoThePalette() {
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor(null));
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor(""));
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor(" \t"));
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor("minecraft"));
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor("impetus"));
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor("sodium"));
        assertEquals(0xFF5FD0A8, DefaultColors.getModAccentColor("extras"));
        assertEquals(0xFF80CBC4, DefaultColors.getModAccentColor("coarctatio"));
        assertEquals(0xFFEBCB8B, DefaultColors.getModAccentColor("fulgor"));
        assertEquals(0xFF88C0D0, DefaultColors.getModAccentColor("equilibrium"));
        assertEquals(0xFFD86AFF, DefaultColors.getModAccentColor("umbra"));
        int hashed = DefaultColors.getModAccentColor("somebodys_mod");
        assertEquals(hashed, DefaultColors.getModAccentColor("somebodys_mod"));
        assertNotEquals(0, hashed);
        ConfigThemeRegistry.register(ConfigTheme.builder("somebodys_mod").accentColor(0xFF010203).build());
        assertEquals(0xFF010203, DefaultColors.getModAccentColor("somebodys_mod"));
    }

    @Test
    void activatedColoursFollowTheApiDefault() {
        assertEquals(ConfigTheme.DEFAULT_ACCENT_COLOR, DefaultColors.ELEMENT_ACTIVATED);
        // Same RGB, alpha dropped to 0xB0
        assertEquals(0xB000CBCB, DefaultColors.ELEMENT_ACTIVATED_DIM);
    }

    @Test
    void registeredThemesOverrideBuiltInAccents() {
        // The registry is asked before the fixed table, so a mod can restyle even the built-in ids
        ConfigThemeRegistry.register(new ConfigTheme("umbra", 0xFF010203));
        ConfigThemeRegistry.register(new ConfigTheme("minecraft", 0xFF040506));
        assertEquals(0xFF010203, DefaultColors.getModAccentColor("umbra"));
        assertEquals(0xFF040506, DefaultColors.getModAccentColor("minecraft"));
        // Mods without a theme keep their fixed accents
        assertEquals(0xFFEBCB8B, DefaultColors.getModAccentColor("fulgor"));
    }

    @Test
    void idsAreMatchedWhateverTheirCaseOrPadding() {
        assertEquals(0xFFD86AFF, DefaultColors.getModAccentColor("UMBRA"));
        assertEquals(0xFFD86AFF, DefaultColors.getModAccentColor(" Umbra\t"));
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor("Minecraft"));
        // Unknown mods hash the canonical id, so every spelling lands on one palette entry
        assertEquals(DefaultColors.getModAccentColor("somebodys_mod"), DefaultColors.getModAccentColor(" Somebodys_Mod "));
        ConfigThemeRegistry.register(new ConfigTheme("MyMod", 0xFF010203));
        assertEquals(0xFF010203, DefaultColors.getModAccentColor("mymod"));
        assertEquals(0xFF010203, DefaultColors.getModAccentColor("MYMOD"));
    }
}
