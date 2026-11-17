package com.bdmajora.impetus.engine.impl.gui.theme;

import com.bdmajora.impetus.api.config.theme.ConfigTheme;
import com.bdmajora.impetus.api.config.theme.ConfigThemeRegistry;
import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DefaultColorsTest {
    // Drops registered themes.
    @AfterEach
    void clearRegistry() {
        Statics.set(ConfigThemeRegistry.class, "table", Statics.get(ConfigThemeRegistry.class, "EMPTY"));
    }

    @Test
    void knownModsHaveFixedAccentsAndOthersHashIntoThePalette() {
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor(null));
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor(""));
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor(" \t"));
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor("minecraft"));
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor("impetus"));
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor("sodium"));
        // Checks every Impetus module shares the Impetus accent.
        for (String module : new String[] {"extras", "coarctatio", "fulgor", "equilibrium", "umbra", "dynamiclights", "linuxextras"}) {
            assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor(module), module);
        }
        int hashed = DefaultColors.getModAccentColor("somebodys_mod");
        assertEquals(hashed, DefaultColors.getModAccentColor("somebodys_mod"));
        assertNotEquals(0, hashed);
        ConfigThemeRegistry.register(ConfigTheme.builder("somebodys_mod").accentColor(0xFF010203).build());
        assertEquals(0xFF010203, DefaultColors.getModAccentColor("somebodys_mod"));
    }

    @Test
    void activatedColoursFollowTheApiDefault() {
        assertEquals(ConfigTheme.DEFAULT_ACCENT_COLOR, DefaultColors.ELEMENT_ACTIVATED);
        // Checks the alpha.
        assertEquals(0xB000CBCB, DefaultColors.ELEMENT_ACTIVATED_DIM);
    }

    @Test
    void registeredThemesOverrideBuiltInAccents() {
        // Checks the registry first.
        ConfigThemeRegistry.register(new ConfigTheme("umbra", 0xFF010203));
        ConfigThemeRegistry.register(new ConfigTheme("minecraft", 0xFF040506));
        assertEquals(0xFF010203, DefaultColors.getModAccentColor("umbra"));
        assertEquals(0xFF040506, DefaultColors.getModAccentColor("minecraft"));
        // Checks a module without a theme keeps the Impetus accent.
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor("fulgor"));
    }

    @Test
    void idsAreMatchedWhateverTheirCaseOrPadding() {
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor("UMBRA"));
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor(" Umbra\t"));
        assertEquals(DefaultColors.getModAccentColor("somebodys_mod"), DefaultColors.getModAccentColor("SOMEBODYS_MOD"));
        assertEquals(DefaultColors.ELEMENT_ACTIVATED, DefaultColors.getModAccentColor("Minecraft"));
        // Checks unknown mods.
        assertEquals(DefaultColors.getModAccentColor("somebodys_mod"), DefaultColors.getModAccentColor(" Somebodys_Mod "));
        ConfigThemeRegistry.register(new ConfigTheme("MyMod", 0xFF010203));
        assertEquals(0xFF010203, DefaultColors.getModAccentColor("mymod"));
        assertEquals(0xFF010203, DefaultColors.getModAccentColor("MYMOD"));
    }
}
