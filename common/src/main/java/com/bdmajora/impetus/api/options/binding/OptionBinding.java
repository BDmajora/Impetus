package com.bdmajora.impetus.api.options.binding;

// Reads and writes an option's value on a storage object
public interface OptionBinding<S, T> {
    // Sets the stored value to the given value and never receives null
    void setValue(S storage, T value);

    // Reads the value from the storage object without writing to it and may return null
    // Called by every OptionImpl.getValue() and hasChanged()
    T getValue(S storage);
}