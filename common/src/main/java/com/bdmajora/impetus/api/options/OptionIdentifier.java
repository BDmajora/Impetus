package com.bdmajora.impetus.api.options;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class OptionIdentifier<T> {
    private final String modId;
    private final String path;
    private final Class<T> clz;

    private static final ObjectOpenHashSet<OptionIdentifier<?>> IDENTIFIERS = new ObjectOpenHashSet<>();

    // Sentinel identifier used instead of returning null
    public static final OptionIdentifier<Void> EMPTY = create("", "", Void.class);

    private OptionIdentifier(String modId, String path, Class<T> clz) {
        this.modId = modId;
        this.path = path;
        this.clz = clz;
    }

    // Owning mod
    public String getModId() {
        return this.modId;
    }

    // Path within the mod
    public String getPath() {
        return this.path;
    }

    // Value type, Void for groups and pages
    public Class<T> getType() {
        return this.clz;
    }

    // Untyped id for groups and pages
    public static OptionIdentifier<Void> create(String modId, String path) {
        return create(modId, path, void.class);
    }

    // Interned, so the same id always yields the same instance
    @SuppressWarnings("unchecked")
    public static synchronized <T> OptionIdentifier<T> create(String modId, String path, Class<T> clz) {
        // Interns identifiers so equal (modId, path) pairs share one instance; matches() below relies on reference equality
        OptionIdentifier<T> ourIdentifier = new OptionIdentifier<>(modId, path, clz);
        OptionIdentifier<T> oldIdentifier = (OptionIdentifier<T>)IDENTIFIERS.addOrGet(ourIdentifier);
        if(oldIdentifier != null && oldIdentifier.clz != ourIdentifier.clz) {
            throw new IllegalArgumentException(String.format("OptionIdentifier '%s' created with differing class type %s from existing instance %s", ourIdentifier, ourIdentifier.clz, oldIdentifier.clz));
        }
        return oldIdentifier;
    }

    // Non-null and not the empty placeholder
    public static boolean isPresent(@Nullable OptionIdentifier<?> id) {
        return id != null && id != EMPTY;
    }

    // Same mod and path, ignoring type; reference equality suffices because create interns on (modId, path)
    public boolean matches(OptionIdentifier<?> other) {
        return this == other;
    }

    // Unchecked retype
    @SuppressWarnings("unchecked")
    public <U> OptionIdentifier<U> cast() {
        return (OptionIdentifier<U>)this;
    }

    // modId:path
    @Override
    public String toString() {
        return this.modId + ":" + this.path;
    }

    // By mod and path; the type is checked once at interning instead
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OptionIdentifier<?> that = (OptionIdentifier<?>) o;
        return Objects.equals(modId, that.modId) && Objects.equals(path, that.path);
    }

    // By mod and path
    @Override
    public int hashCode() {
        return Objects.hash(modId, path);
    }
}
