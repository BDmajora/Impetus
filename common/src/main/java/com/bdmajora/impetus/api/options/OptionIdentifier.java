package com.bdmajora.impetus.api.options;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

// Interned id made of a mod id and path and a value type for an option or group or page
public final class OptionIdentifier<T> {
    private final String modId;
    private final String path;
    private final Class<T> clz;

    // Interned ids keyed by mod id and then by path and guarded by the class lock
    private static final Object2ObjectOpenHashMap<String, Object2ObjectOpenHashMap<String, OptionIdentifier<?>>> IDENTIFIERS = new Object2ObjectOpenHashMap<>();

    // Placeholder id used in place of null
    public static final OptionIdentifier<Void> EMPTY = create("", "", Void.class);

    // Stores the mod id and path and value type
    private OptionIdentifier(String modId, String path, Class<T> clz) {
        this.modId = modId;
        this.path = path;
        this.clz = clz;
    }

    // Returns the id of the mod that owns this id
    public String getModId() {
        return this.modId;
    }

    // Returns the path within the mod
    public String getPath() {
        return this.path;
    }

    // Returns the value type which is void for groups and pages
    public Class<T> getType() {
        return this.clz;
    }

    // Returns the untyped interned id for a group or page
    public static OptionIdentifier<Void> create(String modId, String path) {
        return create(modId, path, void.class);
    }

    // Returns the interned id for the mod id and path and rejects a type that differs from the existing id
    @SuppressWarnings("unchecked")
    public static <T> OptionIdentifier<T> create(String modId, String path, Class<T> clz) {
        Objects.requireNonNull(clz, "Type must not be null");
        OptionIdentifier<?> id = interned(modId, path, clz);
        // Rejects an existing id created with a different type
        if (id.clz != clz) {
            throw new IllegalArgumentException(String.format("OptionIdentifier '%s' created with differing class type %s from existing instance %s", id, clz, id.clz));
        }
        return (OptionIdentifier<T>) id;
    }

    // Returns the interned id for the mod id and path with any type or creates an untyped one
    public static OptionIdentifier<?> intern(String modId, String path) {
        return interned(modId, path, void.class);
    }

    // Returns the interned id for the pair and creates it with the given type when none exists
    private static synchronized OptionIdentifier<?> interned(String modId, String path, Class<?> clz) {
        // Rejects a null mod id or path before the table is read
        Objects.requireNonNull(modId, "Mod id must not be null");
        Objects.requireNonNull(path, "Path must not be null");
        Object2ObjectOpenHashMap<String, OptionIdentifier<?>> paths = IDENTIFIERS.get(modId);
        if (paths == null) {
            paths = new Object2ObjectOpenHashMap<>();
            IDENTIFIERS.put(modId, paths);
        }
        OptionIdentifier<?> id = paths.get(path);
        if (id == null) {
            id = new OptionIdentifier<>(modId, path, clz);
            paths.put(path, id);
        }
        return id;
    }

    // Returns true for an id that is not null and not the placeholder
    public static boolean isPresent(@Nullable OptionIdentifier<?> id) {
        return id != null && id != EMPTY;
    }

    // Returns true when both ids have the same mod id and path
    public boolean matches(OptionIdentifier<?> other) {
        return this == other;
    }

    // Returns this id cast to another value type without a check
    @SuppressWarnings("unchecked")
    public <U> OptionIdentifier<U> cast() {
        return (OptionIdentifier<U>)this;
    }

    // Returns the mod id and path joined by a colon
    @Override
    public String toString() {
        return this.modId + ":" + this.path;
    }

    // Returns true only for the same instance
    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    // Returns a hash of the mod id and path
    @Override
    public int hashCode() {
        return 31 * (31 + this.modId.hashCode()) + this.path.hashCode();
    }
}
