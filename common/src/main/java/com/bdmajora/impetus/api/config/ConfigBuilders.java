package com.bdmajora.impetus.api.config;

import com.bdmajora.impetus.api.options.OptionIdentifier;
import com.bdmajora.impetus.api.options.structure.OptionGroup;
import com.bdmajora.impetus.api.options.structure.OptionImpl;
import com.bdmajora.impetus.api.options.structure.OptionStorage;
import com.bdmajora.impetus.engine.impl.gui.framework.TextComponent;

import java.util.Objects;

// Creates page, group, option and storage builders, validating every argument before an id is created
public final class ConfigBuilders {
    // Prevents instantiation
    private ConfigBuilders() {
    }

    // Returns a page builder with an id created from the mod id and path
    public static ConfigPageBuilder page(String modId, String path, TextComponent name) {
        checkParts(modId, path);
        Objects.requireNonNull(name, "Page name must not be null");
        return new ConfigPageBuilder(OptionIdentifier.create(modId, path), name);
    }

    // Returns a page builder using an existing page id
    public static ConfigPageBuilder page(OptionIdentifier<Void> id, TextComponent name) {
        checkUntyped(Objects.requireNonNull(id, "Page id must not be null"), "Page");
        Objects.requireNonNull(name, "Page name must not be null");
        return new ConfigPageBuilder(id, name);
    }

    // Returns a group builder with an id created from the mod id and path
    public static OptionGroup.Builder group(String modId, String path) {
        checkParts(modId, path);
        return OptionGroup.createBuilder().setId(OptionIdentifier.create(modId, path));
    }

    // Returns a group builder using an existing group id
    public static OptionGroup.Builder group(OptionIdentifier<Void> id) {
        checkUntyped(Objects.requireNonNull(id, "Group id must not be null"), "Group");
        return OptionGroup.createBuilder().setId(id);
    }

    // Returns an option builder for the given value type and storage, with an id created from the mod id, path and type
    public static <S, T> OptionImpl.Builder<S, T> option(String modId, String path, Class<T> type, OptionStorage<S> storage) {
        checkParts(modId, path);
        checkValueType(type);
        Objects.requireNonNull(storage, "Option storage must not be null");
        return OptionImpl.createBuilder(type, storage)
                .setId(OptionIdentifier.create(modId, path, type));
    }

    // Returns an option builder for the given value type and storage, using an existing option id
    public static <S, T> OptionImpl.Builder<S, T> option(OptionIdentifier<T> id, Class<T> type, OptionStorage<S> storage) {
        Objects.requireNonNull(id, "Option id must not be null");
        checkValueType(type);
        // Rejects an id whose type differs from the option type, unless the id is untyped
        if (id.getType() != type && id.getType() != void.class) {
            throw new IllegalArgumentException("Option id '" + id + "' has type " + id.getType() + ", not " + type);
        }
        Objects.requireNonNull(storage, "Option storage must not be null");
        return OptionImpl.createBuilder(type, storage)
                .setId(id);
    }

    // Returns storage that wraps the data object and runs saveAction when changes are saved
    public static <T> ConfigStorage<T> storage(T data, Runnable saveAction) {
        Objects.requireNonNull(data, "Config data must not be null");
        Objects.requireNonNull(saveAction, "Save action must not be null");
        return ConfigStorage.of(data, saveAction);
    }

    // Rejects a null or blank mod id or path
    private static void checkParts(String modId, String path) {
        if (Objects.requireNonNull(modId, "Mod id must not be null").isBlank()) {
            throw new IllegalArgumentException("Mod id must not be blank");
        }
        if (Objects.requireNonNull(path, "Path must not be null").isBlank()) {
            throw new IllegalArgumentException("Path must not be blank");
        }
    }

    // Rejects a page or group id whose type is not void
    private static void checkUntyped(OptionIdentifier<Void> id, String kind) {
        if (id.getType() != void.class) {
            throw new IllegalArgumentException(kind + " id '" + id + "' has type " + id.getType() + ", but page and group ids have type void");
        }
    }

    // Rejects a null, void or Void option type
    private static void checkValueType(Class<?> type) {
        if (Objects.requireNonNull(type, "Option type must not be null") == void.class || type == Void.class) {
            throw new IllegalArgumentException("Option type must not be " + type);
        }
    }
}