package com.bdmajora.dynamiclights;

import com.bdmajora.impetus.impl.gui.Localized;

// Update rate of the dynamic lights.
public enum DynamicLightsMode implements Localized {
    // Lights are off.
    OFF(0, "impetus.options.dynamiclights.mode.off"),
    // Lights update every half second.
    SLOW(500, "impetus.options.dynamiclights.mode.slow"),
    // Lights update every quarter second.
    FAST(250, "impetus.options.dynamiclights.mode.fast"),
    // Lights update every tick.
    REALTIME(0, "impetus.options.dynamiclights.mode.realtime");

    // Milliseconds between updates.
    private final int delay;
    // Translation key of the name.
    private final String key;

    // Creates a mode.
    DynamicLightsMode(int delay, String key) {
        this.delay = delay;
        this.key = key;
    }

    // Returns whether the mode updates lights.
    public boolean isEnabled() {
        return this != OFF;
    }

    // Returns whether updates wait between steps.
    public boolean hasDelay() {
        return this.delay != 0;
    }

    // Returns the milliseconds between updates.
    public int getDelay() {
        return this.delay;
    }

    // Returns the translation key of the name.
    @Override
    public String translationKey() {
        return this.key;
    }
}
