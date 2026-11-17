package com.bdmajora.impetus.engine.impl.gui.theme;

import com.bdmajora.impetus.api.config.theme.ConfigTheme;
import com.bdmajora.impetus.api.config.theme.ConfigThemeRegistry;

// Colors shared by the GUI and the accent colors of other mods.
public class DefaultColors {
    // Accent color of controls and unthemed mods.
    public static final int ELEMENT_ACTIVATED = ConfigTheme.DEFAULT_ACCENT_COLOR;
    // Dimmed accent color.
    public static final int ELEMENT_ACTIVATED_DIM = 0xB0000000 | (ELEMENT_ACTIVATED & 0x00FFFFFF);
    // Text color of disabled elements.
    public static final int TEXT_DISABLED = 0xFF9A9A9A;
    // Background color of idle elements.
    public static final int BACKGROUND_DEFAULT = 0x90000000;
    // Background color of hovered elements.
    public static final int BACKGROUND_HOVERED = 0xE0202020;
    // Background color of disabled elements.
    public static final int BACKGROUND_DISABLED = 0x60000000;

    // Colors assigned to unknown mods by their id.
    private static final int[] MOD_ACCENT_PALETTE = {
            0xFF80CBC4,
            0xFFFFB86C,
            0xFFA3BE8C,
            0xFFEBCB8B,
            0xFF88C0D0,
            0xFFD08770
    };

    // Prevents instantiation
    private DefaultColors() {
    }

    // Returns the accent color of a mod.
    public static int getModAccentColor(String modId) {
        // Uses the default for a missing or blank id
        if (modId == null || modId.isBlank()) {
            return ELEMENT_ACTIVATED;
        }

        // Looks up the registered color by the canonical id
        String id = ConfigTheme.canonicalModId(modId);
        var registered = ConfigThemeRegistry.getAccentColor(id);
        if (registered.isPresent()) {
            return registered.getAsInt();
        }

        return switch (id) {
            case "minecraft", "impetus", "sodium", "extras", "coarctatio", "fulgor", "equilibrium", "umbra",
                 "dynamiclights", "linuxextras" -> ELEMENT_ACTIVATED;
            default -> MOD_ACCENT_PALETTE[Math.floorMod(id.hashCode(), MOD_ACCENT_PALETTE.length)];
        };
    }
}
