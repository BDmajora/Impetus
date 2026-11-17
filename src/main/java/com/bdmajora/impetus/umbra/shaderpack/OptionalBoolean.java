package com.bdmajora.impetus.umbra.shaderpack;

import java.util.function.BooleanSupplier;

// A shaders.properties boolean that may be unset.
public enum OptionalBoolean {
    // Not set by the pack.
    DEFAULT,
    // Set to false.
    FALSE,
    // Set to true.
    TRUE;

    // Returns the value or the default when it is unset.
    public boolean orElse(boolean defaultValue) {
        if (this == DEFAULT) {
            return defaultValue;
        }

        return this == TRUE;
    }

    // Returns the value or the supplier's answer when it is unset.
    public boolean orElseGet(BooleanSupplier defaultValue) {
        if (this == DEFAULT) {
            return defaultValue.getAsBoolean();
        }

        return this == TRUE;
    }
}
