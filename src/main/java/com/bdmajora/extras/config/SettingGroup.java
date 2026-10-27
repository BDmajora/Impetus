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

// Settings of one group of the Extras config stored under one Forge category
final class SettingGroup<S> {
    // Name of the Forge category
    private final String category;
    // Reader of the group from a whole config
    private final Function<ExtrasConfig, S> owner;
    // Bindings of the fields of the group in declaration order
    private final Setting<S>[] settings;

    // Stores the category and the owner and the settings
    private SettingGroup(String category, Function<ExtrasConfig, S> owner, Setting<S>[] settings) {
        this.category = category;
        this.owner = owner;
        this.settings = settings;
    }

    // Creates a builder that reads every default from a fresh instance made by the factory
    static <S> Builder<S> builder(String category, Function<ExtrasConfig, S> owner, Supplier<S> factory) {
        return new Builder<>(category, owner, factory);
    }

    // Reads every setting of the group from the category
    void load(Configuration config, ExtrasConfig options) {
        S group = owner.apply(options);
        for (Setting<S> setting : settings) {
            setting.load(config, category, group);
        }
    }

    // Writes every setting of the group into the category
    void save(Configuration config, ExtrasConfig options) {
        S group = owner.apply(options);
        ConfigCategory section = config.getCategory(category);
        for (Setting<S> setting : settings) {
            setting.save(config, section, group);
        }
    }

    // Collects the settings of one group with defaults read from a pristine instance
    static final class Builder<S> {
        // Name of the Forge category
        private final String category;
        // Reader of the group from a whole config
        private final Function<ExtrasConfig, S> owner;
        // Instance that holds the default of every field
        private final S defaults;
        // Settings added so far
        private final List<Setting<S>> settings = new ArrayList<>();
        // Keys added so far
        private final Set<String> keys = new HashSet<>();

        // Rejects a null part and stores a fresh instance of the group as the defaults
        private Builder(String category, Function<ExtrasConfig, S> owner, Supplier<S> factory) {
            this.category = Objects.requireNonNull(category, "category");
            this.owner = Objects.requireNonNull(owner, "owner");
            this.defaults = Objects.requireNonNull(factory.get(), "factory result");
        }

        // Adds a boolean setting
        Builder<S> bool(String key, String comment, Predicate<S> getter, BooleanSetting.Setter<S> setter) {
            return add(new BooleanSetting<>(key, getter.test(defaults), comment, getter, setter));
        }

        // Adds an integer setting clamped to the inclusive range
        Builder<S> integer(String key, int min, int max, String comment, ToIntFunction<S> getter, ObjIntConsumer<S> setter) {
            return add(new IntSetting<>(key, getter.applyAsInt(defaults), min, max, comment, getter, setter));
        }

        // Adds an enum setting stored by ordinal
        <E extends Enum<E>> Builder<S> choice(String key, String comment, Function<S, E> getter, BiConsumer<S, E> setter) {
            return add(new ChoiceSetting<>(key, getter.apply(defaults), comment, getter, setter));
        }

        // Adds a string list setting
        Builder<S> list(String key, String comment, Function<S, String[]> getter, BiConsumer<S, String[]> setter) {
            return add(new ListSetting<>(key, getter.apply(defaults), comment, getter, setter));
        }

        // Creates the group from the settings added so far and rejects a group without settings
        @SuppressWarnings("unchecked")
        SettingGroup<S> build() {
            if (settings.isEmpty()) {
                throw new IllegalStateException(category + " holds no settings");
            }
            return new SettingGroup<>(category, owner, settings.toArray(new Setting[0]));
        }

        // Appends the setting and rejects a key the group already holds
        private Builder<S> add(Setting<S> setting) {
            if (!keys.add(setting.key())) {
                throw new IllegalArgumentException(category + " already holds " + setting.key());
            }
            settings.add(setting);
            return this;
        }
    }
}
