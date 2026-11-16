package com.bdmajora.impetus.impl.config;

import net.minecraftforge.common.config.Configuration;

import java.util.function.Consumer;
import java.util.function.Supplier;

// Option stored in a Forge configuration file.
public interface ConfigProperty {
    // Reads the option from a configuration.
    void load(Configuration config);

    // Writes the option into a configuration.
    void save(Configuration config);

    // Creates a boolean option.
    static ConfigProperty bool(String category, String key, boolean defaultValue, String comment,
                               Consumer<Boolean> setter, Supplier<Boolean> getter) {
        return new BooleanProperty(category, key, defaultValue, comment, setter, getter);
    }

    // Creates an int option limited to a range.
    static ConfigProperty integer(String category, String key, int defaultValue, int min, int max, String comment,
                                  Consumer<Integer> setter, Supplier<Integer> getter) {
        return new IntProperty(category, key, defaultValue, min, max, comment, setter, getter);
    }

    // Creates an enum option stored by ordinal.
    static <T extends Enum<T>> ConfigProperty enumeration(String category, String key, T[] values, T defaultValue, String comment,
                                                          Consumer<T> setter, Supplier<T> getter) {
        return new EnumProperty<>(category, key, values, defaultValue, comment, setter, getter);
    }

    // Boolean option.
    record BooleanProperty(String category, String key, boolean defaultValue, String comment,
                           Consumer<Boolean> setter, Supplier<Boolean> getter) implements ConfigProperty {
        // Reads the value and passes it to the setter.
        @Override
        public void load(Configuration config) {
            setter.accept(config.getBoolean(key, category, defaultValue, comment));
        }

        // Writes the value of the getter.
        @Override
        public void save(Configuration config) {
            config.get(category, key, defaultValue).set(getter.get());
        }
    }

    // Int option limited to a range.
    record IntProperty(String category, String key, int defaultValue, int min, int max, String comment,
                       Consumer<Integer> setter, Supplier<Integer> getter) implements ConfigProperty {
        // Reads the value limited to the range and passes it to the setter.
        @Override
        public void load(Configuration config) {
            setter.accept(config.getInt(key, category, defaultValue, min, max, comment));
        }

        // Writes the value of the getter.
        @Override
        public void save(Configuration config) {
            config.get(category, key, defaultValue).set(getter.get());
        }
    }

    // Enum option stored by ordinal.
    record EnumProperty<T extends Enum<T>>(String category, String key, T[] values, T defaultValue, String comment,
                                           Consumer<T> setter, Supplier<T> getter) implements ConfigProperty {
        // Rejects a null or empty constant array.
        public EnumProperty {
            if (values.length == 0) {
                throw new IllegalArgumentException("Enum option " + key + " needs at least one constant");
            }
        }

        // Reads the ordinal limited to the constants and passes the constant to the setter.
        @Override
        public void load(Configuration config) {
            setter.accept(values[config.getInt(key, category, defaultValue.ordinal(), 0, values.length - 1, comment)]);
        }

        // Writes the ordinal of the value of the getter.
        @Override
        public void save(Configuration config) {
            config.get(category, key, defaultValue.ordinal()).set(getter.get().ordinal());
        }
    }
}
