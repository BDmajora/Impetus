package com.bdmajora.impetus.api.options.structure;

import java.util.Objects;
import java.util.Set;

// Holds the object option values are read from and written to and saves it
@FunctionalInterface
public interface OptionStorage<T> {
    // Returns the object that option bindings read and write
    T getData();

    // Saves the data and does nothing by default
    default void save() {
    }

    // Saves the data with the flags of every option that was written and calls save() by default
    default void save(Set<OptionFlag> flags) {
        // Rejects a null set of flags
        Objects.requireNonNull(flags, "Flags must not be null");
        save();
    }
}