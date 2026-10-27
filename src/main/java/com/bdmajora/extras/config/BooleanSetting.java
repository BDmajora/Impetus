package com.bdmajora.extras.config;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

import java.util.Objects;
import java.util.function.Predicate;

// Boolean field stored as a Forge boolean property
record BooleanSetting<S>(String key, boolean defaultValue, String comment, Predicate<S> getter, Setter<S> setter)
        implements Setting<S> {
    // Rejects a null key or comment or accessor
    BooleanSetting {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(comment, "comment");
        Objects.requireNonNull(getter, "getter");
        Objects.requireNonNull(setter, "setter");
    }

    // Reads the boolean and keeps the default for a missing or unreadable value
    @Override
    public void load(Configuration config, String category, S group) {
        setter.set(group, config.getBoolean(key, category, defaultValue, comment));
    }

    // Writes the boolean of the group and keeps the comment of the property
    @Override
    public void save(Configuration config, ConfigCategory section, S group) {
        boolean value = getter.test(group);
        property(config, section).set(value);
    }

    // Adds the boolean property with its default and its comment
    @Override
    public void create(Configuration config, String category) {
        config.getBoolean(key, category, defaultValue, comment);
    }

    // Writer of one primitive boolean field
    @FunctionalInterface
    interface Setter<S> {
        // Stores the value in the group
        void set(S group, boolean value);
    }
}
