package com.bdmajora.impetus.engine.impl.gl.shader;

import java.util.*;

public class ShaderConstants {
    public static final ShaderConstants EMPTY = ShaderConstants.builder().build();

    private final List<String> defines;

    private ShaderConstants(List<String> defines) {
        this.defines = defines;
    }

    // The #define lines
    public List<String> getDefineStrings() {
        return this.defines;
    }

    // Starts an empty set
    public static ShaderConstants.Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private static final String EMPTY_VALUE = "";

        // Insertion-ordered so the emitted #define block, and anything hashing it, is stable across runs
        private final Map<String, String> constants = new LinkedHashMap<>();

        private Builder() {

        }

        // #define NAME
        public ShaderConstants.Builder add(String name) {
            return this.add(name, EMPTY_VALUE);
        }

        // #define NAME value
        public ShaderConstants.Builder add(String name, String value) {
            String prev = this.constants.putIfAbsent(name, value);
            if (prev != null) {
                throw new IllegalArgumentException("Constant " + name + " is already defined with value " + prev);
            }
            return this;
        }

        // Finalises
        public ShaderConstants build() {
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

            return new ShaderConstants(Collections.unmodifiableList(defines));
        }

        // Bulk value-less defines
        public ShaderConstants.Builder addAll(Collection<String> defines) {
            for (String value : defines) {
                this.add(value);
            }
            return this;
        }

        // Bulk valued defines
        public ShaderConstants.Builder addAll(Map<String, String> defines) {
            defines.forEach(this::add);
            return this;
        }
    }
}
