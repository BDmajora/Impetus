package com.bdmajora.extras.config;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

// Enum field stored as a Forge integer property that holds the ordinal
record ChoiceSetting<S, E extends Enum<E>>(String key, E defaultValue, String comment, Function<S, E> getter,
                                           BiConsumer<S, E> setter, E[] values) implements Setting<S> {
    // Rejects a null part and a value table that does not hold the default at its ordinal
    ChoiceSetting {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(defaultValue, "defaultValue");
        Objects.requireNonNull(comment, "comment");
        Objects.requireNonNull(getter, "getter");
        Objects.requireNonNull(setter, "setter");
        Objects.requireNonNull(values, "values");
        if (defaultValue.ordinal() >= values.length || values[defaultValue.ordinal()] != defaultValue) {
            throw new IllegalArgumentException(key + " values do not hold " + defaultValue);
        }
    }

    // Creates a setting over every constant of the enum of the default
    ChoiceSetting(String key, E defaultValue, String comment, Function<S, E> getter, BiConsumer<S, E> setter) {
        this(key, defaultValue, comment, getter, setter, defaultValue.getDeclaringClass().getEnumConstants());
    }

    // Reads the ordinal clamped to the constants and keeps the default for a missing or unreadable value
    @Override
    public void load(Configuration config, String category, S group) {
        setter.accept(group, values[config.getInt(key, category, defaultValue.ordinal(), 0, values.length - 1, comment)]);
    }

    // Writes the ordinal of the group value and keeps the comment of the property and rejects a null value
    @Override
    public void save(Configuration config, ConfigCategory section, S group) {
        int ordinal = getter.apply(group).ordinal();
        property(config, section).set(ordinal);
    }

    // Adds the ordinal property with its default and its range and its comment
    @Override
    public void create(Configuration config, String category) {
        config.getInt(key, category, defaultValue.ordinal(), 0, values.length - 1, comment);
    }
}
