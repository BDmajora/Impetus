package com.bdmajora.extras.config;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

// Contract of the setting.
interface Setting<S> {
    // Handles the key.
    String key();

    // Loads the value.
    void load(Configuration config, String category, S group);

    // Saves the value.
    void save(Configuration config, ConfigCategory section, S group);

    // Creates the value.
    void create(Configuration config, String category);

    // Handles the property.
    default Property property(Configuration config, ConfigCategory section) {
        Property property = section.get(key());
        if (property == null) {
            create(config, section.getName());
            property = section.get(key());
        }
        return property;
    }
}
