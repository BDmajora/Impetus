package com.bdmajora.impetus.api.options.binding;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

// Option binding that reads and writes a value on the storage object through a getter and setter
public class GenericBinding<S, T> implements OptionBinding<S, T> {
    private final BiConsumer<S, T> setter;
    private final Function<S, T> getter;

    // Stores the setter and getter and rejects either if null
    public GenericBinding(BiConsumer<S, T> setter, Function<S, T> getter) {
        this.setter = Objects.requireNonNull(setter, "Setter must not be null");
        this.getter = Objects.requireNonNull(getter, "Getter must not be null");
    }

    // Writes the value to the storage object with the setter after rejecting a null storage
    @Override
    public void setValue(S storage, T value) {
        // Passes a null value to the setter unchanged
        this.setter.accept(Objects.requireNonNull(storage, "Storage must not be null"), value);
    }

    // Reads the value from the storage object with the getter after rejecting a null storage
    @Override
    public T getValue(S storage) {
        // Returns a null result from the getter unchanged
        return this.getter.apply(Objects.requireNonNull(storage, "Storage must not be null"));
    }
}