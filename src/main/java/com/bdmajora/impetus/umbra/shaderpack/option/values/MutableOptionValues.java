package com.bdmajora.impetus.umbra.shaderpack.option.values;

import com.bdmajora.impetus.umbra.shaderpack.option.OptionSet;

import java.util.HashMap;
import java.util.Map;

// Editable option values.
public class MutableOptionValues extends AbstractOptionValues {
    // Creates a mutable option values from its options and boolean values and string values.
    MutableOptionValues(OptionSet options, Map<String, Boolean> booleanValues, Map<String, String> stringValues) {
        this(options, stringify(booleanValues, stringValues));
    }

    // Creates a mutable option values from its options and values.
    public MutableOptionValues(OptionSet options, Map<String, String> values) {
        super(options, new HashMap<>(), new HashMap<>());
        this.addAll(values);
    }

    // Returns the values of a boolean map as text.
    private static Map<String, String> stringify(Map<String, Boolean> booleanValues, Map<String, String> stringValues) {
        Map<String, String> values = new HashMap<>();
        booleanValues.forEach((k, v) -> values.put(k, Boolean.toString(v)));
        values.putAll(stringValues);
        return values;
    }

    // Returns the options.
    public OptionSet getOptions() {
        return options;
    }

    // Returns the boolean values.
    public Map<String, Boolean> getBooleanValues() {
        return booleanValues;
    }

    // Returns the string values.
    public Map<String, String> getStringValues() {
        return stringValues;
    }

    // Stores every known value of a map.
    public void addAll(Map<String, String> values) {
        options.getBooleanOptions().forEach((name, option) -> {
            String value = values.get(name);
            if (value == null) {
                return;
            }

            boolean defaultValue = option.getOption().getDefaultValue();
            boolean actualValue = "true".equals(value) || (!"false".equals(value) && defaultValue);

            if (actualValue == defaultValue) {
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

            if (value.equals(option.getOption().getDefaultValue())) {
                stringValues.remove(name);
                return;
            }

            stringValues.put(name, value);
        });
    }

    // Returns a frozen copy.
    @Override
    public ImmutableOptionValues toImmutable() {
        return new ImmutableOptionValues(options, booleanValues, stringValues);
    }
}
