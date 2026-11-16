package com.bdmajora.impetus.umbra.shaderpack.option;

import com.bdmajora.impetus.umbra.shaderpack.option.values.OptionValues;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Named set of option values and disabled programs of a shader pack.
public final class Profile {
    // Name of the profile.
    public final String name;
    // Number of option values used to rank matching profiles.
    public final int precedence;
    // Option values by option name.
    public final Map<String, String> optionValues;
    // Programs the profile turns off.
    public final List<String> disabledPrograms;

    // Creates a profile from frozen values.
    private Profile(String name, Map<String, String> optionValues, List<String> disabledPrograms) {
        this.name = name;
        this.optionValues = optionValues;
        this.precedence = optionValues.size();
        this.disabledPrograms = disabledPrograms;
    }

    // Returns whether every known option of the profile has its current value.
    public boolean matches(OptionSet options, OptionValues values) {
        for (Map.Entry<String, String> entry : this.optionValues.entrySet()) {
            String option = entry.getKey();
            String value = entry.getValue();

            if (options.getBooleanOptions().containsKey(option)) {
                boolean currentValue = values.getBooleanValueOrDefault(option);

                if (!Boolean.toString(currentValue).equals(value)) {
                    return false;
                }
            }
            if (options.getStringOptions().containsKey(option)) {
                String currentValue = values.getStringValueOrDefault(option);

                if (!value.equals(currentValue)) {
                    return false;
                }
            }
        }

        return true;
    }

    // Builder of a profile.
    public static class Builder {
        // Name of the profile.
        private final String name;
        // Option values by option name.
        private final Map<String, String> optionValues = new HashMap<>();
        // Programs the profile turns off.
        private final List<String> disabledPrograms = new ArrayList<>();

        // Creates an empty builder.
        public Builder(String name) {
            this.name = name;
        }

        // Sets the value of an option.
        public Builder option(String optionId, String value) {
            this.optionValues.put(optionId, value);

            return this;
        }

        // Adds a program to turn off.
        public Builder disableProgram(String programId) {
            this.disabledPrograms.add(programId);

            return this;
        }

        // Copies the values and disabled programs of another profile.
        public Builder addAll(Profile other) {
            this.optionValues.putAll(other.optionValues);
            this.disabledPrograms.addAll(other.disabledPrograms);

            return this;
        }

        // Returns a profile with copies of the values.
        public Profile build() {
            return new Profile(name,
                    Collections.unmodifiableMap(new HashMap<>(optionValues)),
                    Collections.unmodifiableList(new ArrayList<>(disabledPrograms)));
        }
    }
}
