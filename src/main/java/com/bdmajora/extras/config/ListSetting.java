package com.bdmajora.extras.config;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

// String array field stored as a Forge string list property
record ListSetting<S>(String key, String[] defaultValue, String comment, Function<S, String[]> getter,
                      BiConsumer<S, String[]> setter) implements Setting<S> {
    // Rejects a null part and a default with a null entry
    ListSetting {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(comment, "comment");
        Objects.requireNonNull(getter, "getter");
        Objects.requireNonNull(setter, "setter");
        defaultValue = checkEntries(defaultValue, key).clone();
    }

    // Reads the list and keeps the default for a missing key
    @Override
    public void load(Configuration config, String category, S group) {
        setter.accept(group, config.getStringList(key, category, defaultValue, comment));
    }

    // Writes the list of the group and keeps the comment of the property and rejects a null list or a null entry
    @Override
    public void save(Configuration config, ConfigCategory section, S group) {
        String[] value = checkEntries(getter.apply(group), key);
        property(config, section).set(value);
    }

    // Adds the list property with its default and its comment
    @Override
    public void create(Configuration config, String category) {
        config.getStringList(key, category, defaultValue, comment);
    }

    // Returns the list after it rejects a null list or a null entry
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
