package com.bdmajora.impetus.engine.impl.gl.shader;

import java.util.*;

// Stores immutable shader constant definitions in insertion order.
public class ShaderConstants {
    // Shared constants with no definitions.
    public static final ShaderConstants EMPTY = new ShaderConstants(List.of());

    // Immutable shader definition lines.
    private final List<String> defines;

    // Stores immutable shader definition lines.
    private ShaderConstants(List<String> defines) {
        this.defines = defines;
    }

    // Returns immutable shader definition lines.
    public List<String> getDefineStrings() {
        return this.defines;
    }

    // Creates an empty constant builder.
    public static ShaderConstants.Builder builder() {
        return new Builder();
    }

    // Collects shader constants in insertion order.
    public static class Builder {
        // Value of a constant without replacement text.
        private static final String EMPTY_VALUE = "";

        // Constant names and values in insertion order.
        private final Map<String, String> constants = new LinkedHashMap<>();

        // Snapshot of the current constants or null after an addition.
        private ShaderConstants snapshot = EMPTY;

        // Creates an empty constant builder.
        private Builder() {
        }

        // Adds a constant without replacement text and rejects duplicate names.
        public ShaderConstants.Builder add(String name) {
            return this.add(name, EMPTY_VALUE);
        }

        // Adds a constant with replacement text and rejects null values or duplicate names.
        public ShaderConstants.Builder add(String name, String value) {
            Objects.requireNonNull(value, "value");
            String prev = this.constants.putIfAbsent(name, value);
            if (prev != null) {
                throw new IllegalArgumentException("Constant " + name + " is already defined with value " + prev);
            }
            this.snapshot = null;
            return this;
        }

        // Returns an immutable snapshot of the current constants.
        public ShaderConstants build() {
            if (this.snapshot != null) {
                return this.snapshot;
            }

            List<String> defines = new ArrayList<>(this.constants.size());

            for (Map.Entry<String, String> entry : this.constants.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (value.isEmpty()) {
                    defines.add("#define " + key);
                } else {
                    defines.add("#define " + key + " " + value);
                }
            }

            this.snapshot = new ShaderConstants(Collections.unmodifiableList(defines));
            return this.snapshot;
        }

        // Adds constants without replacement text in collection traversal order.
        public ShaderConstants.Builder addAll(Collection<String> defines) {
            for (String value : defines) {
                this.add(value);
            }
            return this;
        }

        // Adds constants with replacement text in map traversal order.
        public ShaderConstants.Builder addAll(Map<String, String> defines) {
            defines.forEach(this::add);
            return this;
        }
    }
}
