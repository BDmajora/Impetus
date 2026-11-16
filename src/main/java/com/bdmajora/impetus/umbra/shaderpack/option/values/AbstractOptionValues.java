package com.bdmajora.impetus.umbra.shaderpack.option.values;

import com.bdmajora.impetus.umbra.shaderpack.OptionalBoolean;
import com.bdmajora.impetus.umbra.shaderpack.option.OptionSet;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// Base of option values over an option set.
abstract class AbstractOptionValues implements OptionValues {
    // Options the values belong to.
    protected final OptionSet options;
    // Stored boolean values by option name.
    protected final Map<String, Boolean> booleanValues;
    // Stored string values by option name.
    protected final Map<String, String> stringValues;

    // Creates values over stored maps.
    AbstractOptionValues(OptionSet options, Map<String, Boolean> booleanValues, Map<String, String> stringValues) {
        this.options = options;
        this.booleanValues = booleanValues;
        this.stringValues = stringValues;
    }

    // Returns the stored value of a boolean option.
    @Override
    public OptionalBoolean getBooleanValue(String name) {
        Boolean value = booleanValues.get(name);
        if (value == null) {
            return OptionalBoolean.DEFAULT;
        }
        return value ? OptionalBoolean.TRUE : OptionalBoolean.FALSE;
    }

    // Returns the stored value of a string option.
    @Override
    public Optional<String> getStringValue(String name) {
        return Optional.ofNullable(stringValues.get(name));
    }

    // Returns the number of stored values.
    @Override
    public int getOptionsChanged() {
        return this.stringValues.size() + this.booleanValues.size();
    }

    // Returns an editable copy.
    @Override
    public MutableOptionValues mutableCopy() {
        return new MutableOptionValues(options, new HashMap<>(booleanValues), new HashMap<>(stringValues));
    }

    // Returns the option set.
    @Override
    public OptionSet getOptionSet() {
        return options;
    }
}
