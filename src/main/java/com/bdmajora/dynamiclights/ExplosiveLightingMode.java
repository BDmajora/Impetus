package com.bdmajora.dynamiclights;

import com.bdmajora.impetus.impl.gui.Localized;

// Lighting mode of explosive entities.
public enum ExplosiveLightingMode implements Localized {
    // No lighting.
    OFF("impetus.options.dynamiclights.explosive.off"),
    // Constant lighting.
    SIMPLE("impetus.options.dynamiclights.explosive.simple"),
    // Lighting that follows the flash.
    FANCY("impetus.options.dynamiclights.explosive.fancy");

    // Translation key of the name.
    private final String key;

    // Creates a mode.
    ExplosiveLightingMode(String key) {
        this.key = key;
    }

    // Returns whether the mode lights anything.
    public boolean isEnabled() {
        return this != OFF;
    }

    // Returns the translation key of the name.
    @Override
    public String translationKey() {
        return this.key;
    }
}
