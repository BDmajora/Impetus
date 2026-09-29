package com.bdmajora.impetus.engine.impl.gui.theme;

import com.bdmajora.impetus.api.config.theme.ConfigTheme;
import com.bdmajora.impetus.api.config.theme.ConfigThemeRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DefaultColorsTest {
    @Test
    void knownModsHaveFixedAccentsAndOthersHashIntoThePalette() {
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor(null));
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor(""));
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor("minecraft"));
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
}
