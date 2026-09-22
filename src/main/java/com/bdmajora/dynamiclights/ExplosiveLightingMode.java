package com.bdmajora.dynamiclights;

import com.bdmajora.impetus.impl.gui.Localized;

// How a creeper flash or TNT fuse becomes light: SIMPLE is one constant luminance (one rebuild), FANCY ramps it and re-lights on every change, so the cost difference is purely rebuild count
public enum ExplosiveLightingMode implements Localized {
    OFF("impetus.options.dynamiclights.explosive.off"),
    SIMPLE("impetus.options.dynamiclights.explosive.simple"),
    FANCY("impetus.options.dynamiclights.explosive.fancy");

    private final String key;

    ExplosiveLightingMode(String key) {
        this.key = key;
    }

    // Anything but OFF
    public boolean isEnabled() {
        return this != OFF;
    }

    // Lang key for the cycler label
    @Override
    public String translationKey() {
        return this.key;
    }
}
