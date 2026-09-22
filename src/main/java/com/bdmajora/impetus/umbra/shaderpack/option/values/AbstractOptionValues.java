package com.bdmajora.impetus.umbra.shaderpack.option.values;

import com.bdmajora.impetus.umbra.shaderpack.OptionalBoolean;
import com.bdmajora.impetus.umbra.shaderpack.option.OptionSet;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// The two value stores share their shape, a boolean map and a string map holding only what differs from the pack default; only mutability differs
abstract class AbstractOptionValues implements OptionValues {
    protected final OptionSet options;
    protected final Map<String, Boolean> booleanValues;
    protected final Map<String, String> stringValues;

    AbstractOptionValues(OptionSet options, Map<String, Boolean> booleanValues, Map<String, String> stringValues) {
        this.options = options;
        this.booleanValues = booleanValues;
        this.stringValues = stringValues;
    }

    // Changed value, or DEFAULT when at default
    @Override
    public OptionalBoolean getBooleanValue(String name) {
        Boolean value = booleanValues.get(name);
        if (value == null) {
            return OptionalBoolean.DEFAULT;
        }
        return value ? OptionalBoolean.TRUE : OptionalBoolean.FALSE;
    }

    // Changed value, or empty when at default
    @Override
    public Optional<String> getStringValue(String name) {
        return Optional.ofNullable(stringValues.get(name));
    }

    // How many differ from default
    @Override
    public int getOptionsChanged() {
        return this.stringValues.size() + this.booleanValues.size();
    }

    // Independent editable copy
    @Override
    public MutableOptionValues mutableCopy() {
        return new MutableOptionValues(options, new HashMap<>(booleanValues), new HashMap<>(stringValues));
    }

    // The set these values belong to
    @Override
    public OptionSet getOptionSet() {
        return options;
    }
}
