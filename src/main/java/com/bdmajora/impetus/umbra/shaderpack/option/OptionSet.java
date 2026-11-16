package com.bdmajora.impetus.umbra.shaderpack.option;

import com.bdmajora.impetus.umbra.Umbra;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// Options of a pack by name.
public class OptionSet {
    private final Map<String, MergedBooleanOption> booleanOptions;
    private final Map<String, MergedStringOption> stringOptions;

    // Creates an option set from its builder.
    private OptionSet(Builder builder) {
        this.booleanOptions = Collections.unmodifiableMap(new HashMap<>(builder.booleanOptions));
        this.stringOptions = Collections.unmodifiableMap(new HashMap<>(builder.stringOptions));
    }

    // Returns the boolean options.
    public Map<String, MergedBooleanOption> getBooleanOptions() {
        return this.booleanOptions;
    }

    // Returns the string options.
    public Map<String, MergedStringOption> getStringOptions() {
        return this.stringOptions;
    }

    // Returns whether a name is a boolean option.
    public boolean isBooleanOption(String name) {
        return booleanOptions.containsKey(name);
    }

    // Returns a new builder.
    public static Builder builder() {
        return new Builder();
    }

    // Builder that merges options by name.
    public static class Builder {
        private final Map<String, MergedBooleanOption> booleanOptions;
        private final Map<String, MergedStringOption> stringOptions;

        // Creates a builder with default state.
        public Builder() {
            this.booleanOptions = new HashMap<>();
            this.stringOptions = new HashMap<>();
        }

        // Merges every option of another set.
        public void addAll(OptionSet other) {
            if (this.booleanOptions.isEmpty()) {
                this.booleanOptions.putAll(other.booleanOptions);
            } else {
                other.booleanOptions.values().forEach(this::addBooleanOption);
            }

            if (this.stringOptions.isEmpty()) {
                this.stringOptions.putAll(other.stringOptions);
            } else {
                other.stringOptions.values().forEach(this::addStringOption);
            }
        }

        // Adds a boolean option at a location.
        public void addBooleanOption(OptionLocation location, BooleanOption option) {
            addBooleanOption(new MergedBooleanOption(location, option));
        }

        // Merges a boolean option.
        public void addBooleanOption(MergedBooleanOption proposed) {
            addMerged(this.booleanOptions, proposed, "boolean");
        }

        // Adds a string option at a location.
        public void addStringOption(OptionLocation location, StringOption option) {
            addStringOption(new MergedStringOption(location, option));
        }

        // Merges a string option.
        public void addStringOption(MergedStringOption proposed) {
            addMerged(this.stringOptions, proposed, "string");
        }

        // Merges an option into a map and drops it when defaults clash.
        private static <O extends BaseOption, M extends MergedOption<O, M>> void addMerged(Map<String, M> into, M proposed, String kind) {
            String name = proposed.getOption().getName();
            M existing = into.get(name);
            M merged = existing == null ? proposed : existing.merge(proposed);
            if (merged == null) {
                Umbra.logger().warn("Keeping first definition of ambiguous " + kind + " option " + name);
                return;
            }
            into.put(name, merged);
        }

        // Returns the option set.
        public OptionSet build() {
            return new OptionSet(this);
        }
    }
}
