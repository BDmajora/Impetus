package com.bdmajora.impetus.umbra.shaderpack.option.values;

import com.bdmajora.impetus.umbra.Umbra;
import com.bdmajora.impetus.umbra.shaderpack.OptionalBoolean;
import com.bdmajora.impetus.umbra.shaderpack.option.OptionSet;

import java.util.Optional;

// Contract of the option values.
public interface OptionValues {
    // Returns the stored value of a boolean option.
    OptionalBoolean getBooleanValue(String name);

    // Returns the stored value of a string option.
    Optional<String> getStringValue(String name);

    // Returns the stored value or the default of a boolean option.
    default boolean getBooleanValueOrDefault(String name) {
        return getBooleanValue(name).orElseGet(() -> {
            if (!getOptionSet().getBooleanOptions().containsKey(name)) {
                Umbra.logger().warn("Tried to get boolean value for unknown option: " + name + ", defaulting to true!");
                return true;
            }
            return getOptionSet().getBooleanOptions().get(name).getOption().getDefaultValue();
        });
    }

    // Returns the stored value or the default of a string option.
    default String getStringValueOrDefault(String name) {
        return getStringValue(name).orElseGet(() -> getOptionSet().getStringOptions().get(name).getOption().getDefaultValue());
    }

    // Returns the options changed.
    int getOptionsChanged();

    // Returns an editable copy.
    MutableOptionValues mutableCopy();

    // Returns a frozen copy.
    ImmutableOptionValues toImmutable();

    // Returns the option set.
    OptionSet getOptionSet();
}
