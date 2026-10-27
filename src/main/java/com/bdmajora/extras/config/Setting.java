package com.bdmajora.extras.config;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

// Field of a settings group bound to one key of a Forge category
interface Setting<S> {
    // Returns the key of the setting inside its category
    String key();

    // Reads the value from the category into the group
    void load(Configuration config, String category, S group);

    // Writes the value of the group into the section and keeps the comment of the property
    void save(Configuration config, ConfigCategory section, S group);

    // Adds the property with its default and its comment to the category
    void create(Configuration config, String category);

    // Returns the property of the setting and adds it first when the section lacks it
    default Property property(Configuration config, ConfigCategory section) {
        Property property = section.get(key());
        if (property == null) {
            create(config, section.getName());
            property = section.get(key());
        }
        return property;
    }
}
