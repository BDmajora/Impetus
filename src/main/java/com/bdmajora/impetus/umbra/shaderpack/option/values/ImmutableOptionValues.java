package com.bdmajora.impetus.umbra.shaderpack.option.values;

import com.bdmajora.impetus.umbra.shaderpack.option.OptionSet;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// A frozen snapshot of the pack's option values once the user's choices are resolved, immutable since the compile path must see one consistent set for every program. From Iris with unmodifiable HashMaps
public class ImmutableOptionValues extends AbstractOptionValues {
    ImmutableOptionValues(OptionSet options, Map<String, Boolean> booleanValues, Map<String, String> stringValues) {
        super(options, Collections.unmodifiableMap(new HashMap<>(booleanValues)), Collections.unmodifiableMap(new HashMap<>(stringValues)));
    }

    // Already immutable; returns this
    @Override
    public ImmutableOptionValues toImmutable() {
        return this;
    }
}
