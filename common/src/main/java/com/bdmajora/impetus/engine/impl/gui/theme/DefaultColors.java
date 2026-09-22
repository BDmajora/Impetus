package com.bdmajora.impetus.engine.impl.gui.theme;

import com.bdmajora.impetus.api.config.theme.ConfigThemeRegistry;

public class DefaultColors {
    public static final int ELEMENT_ACTIVATED = 0xFF00CBCB;
    public static final int ELEMENT_ACTIVATED_DIM = 0xB000CBCB;
    public static final int TEXT_DISABLED = 0xFF9A9A9A;
    public static final int BACKGROUND_DEFAULT = 0x90000000;
    public static final int BACKGROUND_HOVERED = 0xE0202020;
    public static final int BACKGROUND_DISABLED = 0x60000000;

    // Sidebar text accents sampled from the dominant colour of each subsystem's icon in assets/impetus/textures/gui, so the heading reads as one piece with its logo
    private static final int COARCTATIO_ACCENT = 0xFF80CBC4;
    private static final int FULGOR_ACCENT = 0xFFEBCB8B;
    private static final int EQUILIBRIUM_ACCENT = 0xFF88C0D0;
    private static final int UMBRA_ACCENT = 0xFFD86AFF;
    // Extras is Impetus' own, one step off the core teal so the two groups still read as related
    private static final int EXTRAS_ACCENT = 0xFF5FD0A8;
    private static final int[] MOD_ACCENT_PALETTE = {
            0xFF80CBC4,
            0xFFFFB86C,
            0xFFA3BE8C,
            0xFFEBCB8B,
            0xFF88C0D0,
            0xFFD08770
    };

    private DefaultColors() {
    }

    // Per-mod colour from a fixed table, hashed for unknown mods
    public static int getModAccentColor(String modId) {
        if (modId == null || modId.isEmpty()) {
            return ELEMENT_ACTIVATED;
        }

        var registered = ConfigThemeRegistry.getAccentColor(modId);
        if (registered.isPresent()) {
            return registered.getAsInt();
        }

        return switch (modId) {
            case "minecraft", "impetus", "sodium" -> ELEMENT_ACTIVATED;
            case "extras" -> EXTRAS_ACCENT;
            case "coarctatio" -> COARCTATIO_ACCENT;
            case "fulgor" -> FULGOR_ACCENT;
            case "equilibrium" -> EQUILIBRIUM_ACCENT;
            case "umbra" -> UMBRA_ACCENT;
            default -> MOD_ACCENT_PALETTE[Math.floorMod(modId.hashCode(), MOD_ACCENT_PALETTE.length)];
        };
    }
}
