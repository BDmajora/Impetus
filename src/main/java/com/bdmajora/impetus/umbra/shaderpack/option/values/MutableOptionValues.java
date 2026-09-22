package com.bdmajora.impetus.umbra.shaderpack.option.values;

import com.bdmajora.impetus.umbra.shaderpack.option.OptionSet;

import java.util.HashMap;
import java.util.Map;

// Values equal to the pack default are dropped, so only genuine overrides persist to <pack>.txt; from Umbra with plain HashMaps
public class MutableOptionValues extends AbstractOptionValues {
    MutableOptionValues(OptionSet options, Map<String, Boolean> booleanValues, Map<String, String> stringValues) {
        this(options, stringify(booleanValues, stringValues));
    }

    public MutableOptionValues(OptionSet options, Map<String, String> values) {
        super(options, new HashMap<>(), new HashMap<>());
        this.addAll(values);
    }

    // Both maps as one string map, the form addAll validates against the option set
    private static Map<String, String> stringify(Map<String, Boolean> booleanValues, Map<String, String> stringValues) {
        Map<String, String> values = new HashMap<>();
        booleanValues.forEach((k, v) -> values.put(k, Boolean.toString(v)));
        values.putAll(stringValues);
        return values;
    }

    // The set these values belong to
    public OptionSet getOptions() {
        return options;
    }

    // Changed booleans only
    public Map<String, Boolean> getBooleanValues() {
        return booleanValues;
    }

    // Changed valued options only
    public Map<String, String> getStringValues() {
        return stringValues;
    }

    // Applies a saved config, ignoring names the set does not declare
    public void addAll(Map<String, String> values) {
        options.getBooleanOptions().forEach((name, option) -> {
            String value = values.get(name);
            if (value == null) {
                return;
            }

            boolean defaultValue = option.getOption().getDefaultValue();
            // Anything but true/false is invalid and reads as the default
            boolean actualValue = "true".equals(value) || (!"false".equals(value) && defaultValue);

            if (actualValue == defaultValue) {
                // Just set it to default by removing it from the map
                booleanValues.remove(name);
                return;
            }

            booleanValues.put(name, actualValue);
        });

        options.getStringOptions().forEach((name, option) -> {
            String value = values.get(name);

            if (value == null) {
                return;
            }

            // NB: allowed values are not checked here, matching OptiFine: they only constrain the GUI, while profiles and hand-typed config values are unchecked

            if (value.equals(option.getOption().getDefaultValue())) {
                stringValues.remove(name);
                return;
            }

            stringValues.put(name, value);
        });
    }

    // Snapshot
    @Override
    public ImmutableOptionValues toImmutable() {
        return new ImmutableOptionValues(options, booleanValues, stringValues);
    }
}
