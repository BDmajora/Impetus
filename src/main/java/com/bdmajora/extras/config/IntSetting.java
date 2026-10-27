package com.bdmajora.extras.config;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

import java.util.Objects;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

// Integer field stored as a Forge integer property that is clamped to an inclusive range on load
record IntSetting<S>(String key, int defaultValue, int min, int max, String comment,
                     ToIntFunction<S> getter, ObjIntConsumer<S> setter) implements Setting<S> {
    // Rejects a null key or comment or accessor and a default outside the range
    IntSetting {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(comment, "comment");
        Objects.requireNonNull(getter, "getter");
        Objects.requireNonNull(setter, "setter");
        if (defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException(key + " default " + defaultValue + " lies outside " + min + " and " + max);
        }
    }

    // Reads the integer clamped to the range and keeps the default for a missing or unreadable value
    @Override
    public void load(Configuration config, String category, S group) {
        setter.accept(group, config.getInt(key, category, defaultValue, min, max, comment));
    }

    // Writes the integer of the group and keeps the comment of the property
    @Override
    public void save(Configuration config, ConfigCategory section, S group) {
        int value = getter.applyAsInt(group);
        property(config, section).set(value);
    }

    // Adds the integer property with its default and its range and its comment
    @Override
    public void create(Configuration config, String category) {
        config.getInt(key, category, defaultValue, min, max, comment);
    }
}
