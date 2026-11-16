package com.bdmajora.impetus.umbra.shaderpack.option.values;

import com.bdmajora.impetus.umbra.shaderpack.option.OptionSet;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// Frozen option values.
public class ImmutableOptionValues extends AbstractOptionValues {
    // Creates an immutable option values from its options and boolean values and string values.
    ImmutableOptionValues(OptionSet options, Map<String, Boolean> booleanValues, Map<String, String> stringValues) {
        super(options, Collections.unmodifiableMap(new HashMap<>(booleanValues)), Collections.unmodifiableMap(new HashMap<>(stringValues)));
    }

    // Returns this instance.
    @Override
    public ImmutableOptionValues toImmutable() {
        return this;
    }
}
