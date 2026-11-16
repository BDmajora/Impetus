package com.bdmajora.impetus.umbra.shaderpack;

import java.util.function.BooleanSupplier;

// Choices of the optional boolean setting.
public enum OptionalBoolean {
    // Value that selects default.
    DEFAULT,
    // Value that selects false.
    FALSE,
    // Value that selects true.
    TRUE;

    // Ors the else.
    public boolean orElse(boolean defaultValue) {
        if (this == DEFAULT) {
            return defaultValue;
        }

        return this == TRUE;
    }

    // Ors the else get.
    public boolean orElseGet(BooleanSupplier defaultValue) {
        if (this == DEFAULT) {
            return defaultValue.getAsBoolean();
        }

        return this == TRUE;
    }
}
