package com.google.common.collect;

import java.util.Map;

// Immutable map over parallel key and value arrays.
public final class CoarctatioPropertyMap<K, V> extends ImmutableMap<K, V> {
    // Keys of the map.
    private final Object[] keys;
    // Values of the map.
    private final Object[] values;

    // Creates a coarctatio property map from its keys and values.
    public CoarctatioPropertyMap(Object[] keys, Object[] values) {
        this.keys = keys;
        this.values = values;
    }

    // Returns the slot of a key in the shared key array or -1 when the key is absent.
    private int indexOf(Object key) {
        Object[] keys = this.keys;

        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == key || keys[i].equals(key)) {
                return i;
            }
        }

        return -1;
    }

    // Returns the value.
    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        if (key == null) {
            return null;
        }

        int index = indexOf(key);
        return index < 0 ? null : (V) this.values[index];
    }

    // Returns the size.
    @Override
    public int size() {
        return this.keys.length;
    }

    // Returns whether the map holds no entries.
    @Override
    public boolean isEmpty() {
        return this.keys.length == 0;
    }

    // Returns whether it contains the key.
    @Override
    public boolean containsKey(Object key) {
        return key != null && indexOf(key) >= 0;
    }

    // Returns whether it contains the value.
    @Override
    public boolean containsValue(Object value) {
        if (value == null) {
            return false;
        }

        for (Object candidate : this.values) {
            if (candidate == value || value.equals(candidate)) {
                return true;
            }
        }

        return false;
    }

    // Returns false.
    @Override
    boolean isPartialView() {
        return false;
    }

    // Creates the entry set.
    @Override
    @SuppressWarnings("unchecked")
    ImmutableSet<Map.Entry<K, V>> createEntrySet() {
        Map.Entry<K, V>[] entries = new Map.Entry[this.keys.length];

        for (int i = 0; i < this.keys.length; i++) {
            entries[i] = Maps.immutableEntry((K) this.keys[i], (V) this.values[i]);
        }

        return ImmutableSet.copyOf(entries);
    }

    // Creates the key set.
    @Override
    @SuppressWarnings("unchecked")
    ImmutableSet<K> createKeySet() {
        return ImmutableSet.copyOf((K[]) this.keys);
    }

    // Returns a view of the values.
    @Override
    @SuppressWarnings("unchecked")
    ImmutableCollection<V> createValues() {
        return ImmutableList.copyOf((V[]) this.values);
    }

    // Computes the hash of the code.
    @Override
    public int hashCode() {
        int hash = 0;

        for (int i = 0; i < this.keys.length; i++) {
            Object value = this.values[i];
            hash += this.keys[i].hashCode() ^ (value == null ? 0 : value.hashCode());
        }

        return hash;
    }

    // Returns whether it equals.
    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        if (!(object instanceof Map)) {
            return false;
        }

        Map<?, ?> other = (Map<?, ?>) object;

        if (other.size() != this.keys.length) {
            return false;
        }

        for (int i = 0; i < this.keys.length; i++) {
            Object mine = this.values[i];
            Object theirs = other.get(this.keys[i]);

            if (mine == null ? theirs != null || !other.containsKey(this.keys[i]) : !mine.equals(theirs)) {
                return false;
            }
        }

        return true;
    }

    // Returns the string.
    @Override
    public String toString() {
        if (this.keys.length == 0) {
            return "{}";
        }

        StringBuilder builder = new StringBuilder("{");

        for (int i = 0; i < this.keys.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(this.keys[i]).append('=').append(this.values[i]);
        }

        return builder.append('}').toString();
    }
}
