package com.bdmajora.extras.config;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

// Data of the list setting.
record ListSetting<S>(String key, String[] defaultValue, String comment, Function<S, String[]> getter,
                      BiConsumer<S, String[]> setter) implements Setting<S> {
    ListSetting {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(comment, "comment");
        Objects.requireNonNull(getter, "getter");
        Objects.requireNonNull(setter, "setter");
        defaultValue = checkEntries(defaultValue, key).clone();
    }

    @Override
    public void load(Configuration config, String category, S group) {
        setter.accept(group, config.getStringList(key, category, defaultValue, comment));
    }

    @Override
    public void save(Configuration config, ConfigCategory section, S group) {
        String[] value = checkEntries(getter.apply(group), key);
        property(config, section).set(value);
    }

    @Override
    public void create(Configuration config, String category) {
        config.getStringList(key, category, defaultValue, comment);
    }

    private static String[] checkEntries(String[] list, String key) {
        Objects.requireNonNull(list, key);
        for (String entry : list) {
            if (entry == null) {
                throw new NullPointerException(key + " holds a null entry");
            }
        }
        return list;
    }
}
