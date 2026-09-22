package com.bdmajora.impetus.impl.config;

import com.github.bsideup.jabel.Desugar;
import net.minecraftforge.common.config.Configuration;

import java.util.function.Consumer;
import java.util.function.Supplier;

// A Forge Configuration entry bound to its in-memory field, so a module's load and save walk one declarative table and cannot drift apart
public interface ConfigProperty {
    void load(Configuration config);

    void save(Configuration config);

    static ConfigProperty bool(String category, String key, boolean defaultValue, String comment,
                               Consumer<Boolean> setter, Supplier<Boolean> getter) {
        return new BooleanProperty(category, key, defaultValue, comment, setter, getter);
    }

    // Inclusive range that Configuration clamps to on load
    static ConfigProperty integer(String category, String key, int defaultValue, int min, int max, String comment,
                                  Consumer<Integer> setter, Supplier<Integer> getter) {
        return new IntProperty(category, key, defaultValue, min, max, comment, setter, getter);
    }

    // Stored by ordinal, so the enum is append-only; an out-of-range ordinal falls back on load
    static <T extends Enum<T>> ConfigProperty enumeration(String category, String key, T[] values, T defaultValue, String comment,
                                                          Consumer<T> setter, Supplier<T> getter) {
        return new EnumProperty<>(category, key, values, defaultValue, comment, setter, getter);
    }

    @Desugar
    record BooleanProperty(String category, String key, boolean defaultValue, String comment,
                           Consumer<Boolean> setter, Supplier<Boolean> getter) implements ConfigProperty {
        @Override
        public void load(Configuration config) {
            setter.accept(config.getBoolean(key, category, defaultValue, comment));
        }

        @Override
        public void save(Configuration config) {
            config.get(category, key, defaultValue).set(getter.get());
        }
    }

    @Desugar
    record IntProperty(String category, String key, int defaultValue, int min, int max, String comment,
                       Consumer<Integer> setter, Supplier<Integer> getter) implements ConfigProperty {
        @Override
        public void load(Configuration config) {
            setter.accept(config.getInt(key, category, defaultValue, min, max, comment));
        }

        @Override
        public void save(Configuration config) {
            config.get(category, key, defaultValue).set(getter.get());
        }
    }

    @Desugar
    record EnumProperty<T extends Enum<T>>(String category, String key, T[] values, T defaultValue, String comment,
                                           Consumer<T> setter, Supplier<T> getter) implements ConfigProperty {
        @Override
        public void load(Configuration config) {
            setter.accept(values[config.getInt(key, category, defaultValue.ordinal(), 0, values.length - 1, comment)]);
        }

        @Override
        public void save(Configuration config) {
            config.get(category, key, defaultValue.ordinal()).set(getter.get().ordinal());
        }
    }
}
