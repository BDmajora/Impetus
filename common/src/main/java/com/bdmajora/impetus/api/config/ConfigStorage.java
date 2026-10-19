package com.bdmajora.impetus.api.config;

import com.bdmajora.impetus.api.options.structure.OptionFlag;
import com.bdmajora.impetus.api.options.structure.OptionStorage;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

// Option storage that wraps a config object and runs a save action when options are saved
public final class ConfigStorage<T> implements OptionStorage<T> {
    private final T data;
    private final Consumer<Set<OptionFlag>> saveAction;

    // Stores the config object and save action and rejects either if null
    private ConfigStorage(T data, Consumer<Set<OptionFlag>> saveAction) {
        this.data = Objects.requireNonNull(data, "Config data must not be null");
        this.saveAction = Objects.requireNonNull(saveAction, "Save action must not be null");
    }

    // Creates storage with a save action that runs without receiving the option flags
    public static <T> ConfigStorage<T> of(T data, Runnable saveAction) {
        // Rejects null data before a null save action
        Objects.requireNonNull(data, "Config data must not be null");
        Objects.requireNonNull(saveAction, "Save action must not be null");
        return new ConfigStorage<>(data, flags -> saveAction.run());
    }

    // Creates storage with a save action that receives the flags of the changed options
    public static <T> ConfigStorage<T> of(T data, Consumer<Set<OptionFlag>> saveAction) {
        return new ConfigStorage<>(data, saveAction);
    }

    // Returns the wrapped config object
    @Override
    public T getData() {
        return this.data;
    }

    // Runs the save action with an empty set of flags
    @Override
    public void save() {
        this.saveAction.accept(Set.of());
    }

    // Runs the save action with an immutable copy of the flags of the options that changed
    @Override
    public void save(Set<OptionFlag> flags) {
        // Rejects a null set or null flag and passes the copy to the save action
        this.saveAction.accept(Set.copyOf(Objects.requireNonNull(flags, "Flags must not be null")));
    }
}