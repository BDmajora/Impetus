package com.bdmajora.extras.config;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

// Data of the choice setting.
record ChoiceSetting<S, E extends Enum<E>>(String key, E defaultValue, String comment, Function<S, E> getter,
                                           BiConsumer<S, E> setter, E[] values) implements Setting<S> {
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

    ChoiceSetting(String key, E defaultValue, String comment, Function<S, E> getter, BiConsumer<S, E> setter) {
        this(key, defaultValue, comment, getter, setter, defaultValue.getDeclaringClass().getEnumConstants());
    }

    @Override
    public void load(Configuration config, String category, S group) {
        setter.accept(group, values[config.getInt(key, category, defaultValue.ordinal(), 0, values.length - 1, comment)]);
    }

    @Override
    public void save(Configuration config, ConfigCategory section, S group) {
        int ordinal = getter.apply(group).ordinal();
        property(config, section).set(ordinal);
    }

    @Override
    public void create(Configuration config, String category) {
        config.getInt(key, category, defaultValue.ordinal(), 0, values.length - 1, comment);
    }
}
