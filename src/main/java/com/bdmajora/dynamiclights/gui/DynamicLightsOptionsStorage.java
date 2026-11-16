package com.bdmajora.dynamiclights.gui;

import com.bdmajora.dynamiclights.DynamicLights;
import com.bdmajora.dynamiclights.DynamicLightsConfig;
import com.bdmajora.impetus.api.options.structure.OptionStorage;

// Storage that reads and writes the dynamic lights settings.
public final class DynamicLightsOptionsStorage implements OptionStorage<DynamicLightsConfig> {
    // Returns the settings.
    @Override
    public DynamicLightsConfig getData() {
        return DynamicLights.options();
    }

    // Writes the settings to the file.
    @Override
    public void save() {
        DynamicLights.save();
    }
}
