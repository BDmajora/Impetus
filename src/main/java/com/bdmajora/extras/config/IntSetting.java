package com.bdmajora.extras.config;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

import java.util.Objects;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

// Data of the int setting.
record IntSetting<S>(String key, int defaultValue, int min, int max, String comment,
                     ToIntFunction<S> getter, ObjIntConsumer<S> setter) implements Setting<S> {
    IntSetting {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(comment, "comment");
        Objects.requireNonNull(getter, "getter");
        Objects.requireNonNull(setter, "setter");
        if (defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException(key + " default " + defaultValue + " lies outside " + min + " and " + max);
        }
    }

    @Override
    public void load(Configuration config, String category, S group) {
        setter.accept(group, config.getInt(key, category, defaultValue, min, max, comment));
    }

    @Override
    public void save(Configuration config, ConfigCategory section, S group) {
        int value = getter.applyAsInt(group);
        property(config, section).set(value);
    }

    @Override
    public void create(Configuration config, String category) {
        config.getInt(key, category, defaultValue, min, max, comment);
    }
}
