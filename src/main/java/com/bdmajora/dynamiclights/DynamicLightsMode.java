package com.bdmajora.dynamiclights;

import com.bdmajora.impetus.impl.gui.Localized;

// How often a moving source may re-light nearby chunks; a floor on rebuild scheduling, not a frame budget, since a source at a section corner would otherwise queue eight rebuilds every tick
public enum DynamicLightsMode implements Localized {
    OFF(0, "impetus.options.dynamiclights.mode.off"),
    SLOW(500, "impetus.options.dynamiclights.mode.slow"),
    FAST(250, "impetus.options.dynamiclights.mode.fast"),
    REALTIME(0, "impetus.options.dynamiclights.mode.realtime");

    private final int delay;
    private final String key;

    DynamicLightsMode(int delay, String key) {
        this.delay = delay;
        this.key = key;
    }

    // Anything but OFF
    public boolean isEnabled() {
        return this != OFF;
    }

    // True when #getDelay() is a real floor rather than "every update".
    public boolean hasDelay() {
        return this.delay != 0;
    }

    // Minimum milliseconds between updates for a single source.
    public int getDelay() {
        return this.delay;
    }

    // Lang key for the cycler label
    @Override
    public String translationKey() {
        return this.key;
    }
}
