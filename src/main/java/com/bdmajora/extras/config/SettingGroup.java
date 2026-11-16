package com.bdmajora.extras.config;

import com.bdmajora.extras.ExtrasConfig;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

// Group of settings that reads and writes one section of the config file.
final class SettingGroup<S> {
    // Text of the category option.
    private final String category;
    private final Function<ExtrasConfig, S> owner;
    // Choice of the settings option.
    private final Setting<S>[] settings;

    // Creates a setting group from its category and owner and settings.
    private SettingGroup(String category, Function<ExtrasConfig, S> owner, Setting<S>[] settings) {
        this.category = category;
        this.owner = owner;
        this.settings = settings;
    }

    // Handles the builder.
    static <S> Builder<S> builder(String category, Function<ExtrasConfig, S> owner, Supplier<S> factory) {
        return new Builder<>(category, owner, factory);
    }

    // Loads the value.
    void load(Configuration config, ExtrasConfig options) {
        S group = owner.apply(options);
        for (Setting<S> setting : settings) {
            setting.load(config, category, group);
        }
    }

    // Saves the value.
    void save(Configuration config, ExtrasConfig options) {
        S group = owner.apply(options);
        ConfigCategory section = config.getCategory(category);
        for (Setting<S> setting : settings) {
            setting.save(config, section, group);
        }
    }

    // Builder of a setting group.
    static final class Builder<S> {
        // Text of the category option.
        private final String category;
        private final Function<ExtrasConfig, S> owner;
        // Choice of the defaults option.
        private final S defaults;
        // Choice of the settings option.
        private final List<Setting<S>> settings = new ArrayList<>();
        // Text of the keys option.
        private final Set<String> keys = new HashSet<>();

        // Creates a builder from its category and owner and factory.
        private Builder(String category, Function<ExtrasConfig, S> owner, Supplier<S> factory) {
            this.category = Objects.requireNonNull(category, "category");
            this.owner = Objects.requireNonNull(owner, "owner");
            this.defaults = Objects.requireNonNull(factory.get(), "factory result");
        }

        // Handles the bool.
        Builder<S> bool(String key, String comment, Predicate<S> getter, BooleanSetting.Setter<S> setter) {
            return add(new BooleanSetting<>(key, getter.test(defaults), comment, getter, setter));
        }

        // Handles the integer.
        Builder<S> integer(String key, int min, int max, String comment, ToIntFunction<S> getter, ObjIntConsumer<S> setter) {
            return add(new IntSetting<>(key, getter.applyAsInt(defaults), min, max, comment, getter, setter));
        }

        // Handles the choice.
        <E extends Enum<E>> Builder<S> choice(String key, String comment, Function<S, E> getter, BiConsumer<S, E> setter) {
            return add(new ChoiceSetting<>(key, getter.apply(defaults), comment, getter, setter));
        }

        // Handles the list.
        Builder<S> list(String key, String comment, Function<S, String[]> getter, BiConsumer<S, String[]> setter) {
            return add(new ListSetting<>(key, getter.apply(defaults), comment, getter, setter));
        }

        // Builds the value.
        @SuppressWarnings("unchecked")
        SettingGroup<S> build() {
            if (settings.isEmpty()) {
                throw new IllegalStateException(category + " holds no settings");
            }
            return new SettingGroup<>(category, owner, settings.toArray(new Setting[0]));
        }

        // Adds the value.
        private Builder<S> add(Setting<S> setting) {
            if (!keys.add(setting.key())) {
                throw new IllegalArgumentException(category + " already holds " + setting.key());
            }
            settings.add(setting);
            return this;
        }
    }
}
