package com.bdmajora.extras.config;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

import java.util.Objects;
import java.util.function.Predicate;

// Data of the boolean setting.
record BooleanSetting<S>(String key, boolean defaultValue, String comment, Predicate<S> getter, Setter<S> setter)
        implements Setting<S> {
    BooleanSetting {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(comment, "comment");
        Objects.requireNonNull(getter, "getter");
        Objects.requireNonNull(setter, "setter");
    }

    @Override
    public void load(Configuration config, String category, S group) {
        setter.set(group, config.getBoolean(key, category, defaultValue, comment));
    }

    @Override
    public void save(Configuration config, ConfigCategory section, S group) {
        boolean value = getter.test(group);
        property(config, section).set(value);
    }

    @Override
    public void create(Configuration config, String category) {
        config.getBoolean(key, category, defaultValue, comment);
    }

    // Contract of the setter.
    @FunctionalInterface
    interface Setter<S> {
        // Stores the .
        void set(S group, boolean value);
    }
}
