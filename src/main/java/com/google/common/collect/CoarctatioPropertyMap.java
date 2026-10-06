package com.google.common.collect;

import java.util.Map;

// An ImmutableMap for the property-to-value map every block state carries, sharing one keys array across every state of a block; lives in Guava's package since ImmutableMap's constructor is package-private, so it is injected by ClassDefineTool and may import only com.google.common.collect and java.*
public final class CoarctatioPropertyMap<K, V> extends ImmutableMap<K, V> {
    // Shared across every state of the owning block — never mutate, never hand out
    private final Object[] keys;
    // Parallel to keys: values[i] belongs to keys[i]. Per-state, so this is the only array a state really owns
    private final Object[] values;

    public CoarctatioPropertyMap(Object[] keys, Object[] values) {
        this.keys = keys;
        this.values = values;
    }

    // Linear scan rather than a hash probe: blocks have single-digit property counts, and identity is tried first since properties are interned singletons
    private int indexOf(Object key) {
        Object[] keys = this.keys;

        for (int i = 0; i < keys.length; i++) {
            if (keys[i] == key || keys[i].equals(key)) {
                return i;
            }
        }

        return -1;
    }

    // Linear scan of the shared keys; property counts are single digits
    @Override
    @SuppressWarnings("unchecked")
    public V get(Object key) {
        // ImmutableMap forbids null keys, so a null lookup is a miss rather than a scan
        if (key == null) {
            return null;
        }

        int index = indexOf(key);
        return index < 0 ? null : (V) this.values[index];
    }

    // Keys array length
    @Override
    public int size() {
        return this.keys.length;
    }

    // Only for a block with no properties
    @Override
    public boolean isEmpty() {
        return this.keys.length == 0;
    }

    // Overridden rather than inherited: the inherited version goes through get(), the same scan, but this one skips unwrapping the value
    @Override
    public boolean containsKey(Object key) {
        return key != null && indexOf(key) >= 0;
    }

    // Linear scan of the values
    @Override
    public boolean containsValue(Object value) {
        if (value == null) {
            return false;
        }

        // Straight scan of the values array; the inherited version would build the entry set to answer this
        for (Object candidate : this.values) {
            if (candidate == value || value.equals(candidate)) {
                return true;
            }
        }

        return false;
    }

    // Guava asks this to decide whether the map retains more memory than it exposes and should be copied; both arrays are exactly sized
    @Override
    boolean isPartialView() {
        return false;
    }

    // Only reached when something genuinely iterates the map; ImmutableMap caches the result, so an iterated state pays for an entry set once, the cost vanilla paid unconditionally
    @Override
    @SuppressWarnings("unchecked")
    ImmutableSet<Map.Entry<K, V>> createEntrySet() {
        Map.Entry<K, V>[] entries = new Map.Entry[this.keys.length];

        for (int i = 0; i < this.keys.length; i++) {
            entries[i] = Maps.immutableEntry((K) this.keys[i], (V) this.values[i]);
        }

        return ImmutableSet.copyOf(entries);
    }

    // Guava 21 derived the key and value views from the entry set; Guava 33, which Cleanroom ships, leaves both abstract. Copies, so the shared keys array never escapes
    @Override
    @SuppressWarnings("unchecked")
    ImmutableSet<K> createKeySet() {
        return ImmutableSet.copyOf((K[]) this.keys);
    }

    @Override
    @SuppressWarnings("unchecked")
    ImmutableCollection<V> createValues() {
        return ImmutableList.copyOf((V[]) this.values);
    }

    // Must be overridden, the whole reason the class exists: ImmutableMap.hashCode() lazily builds AND caches an entry set, StateImplementation.hashCode() delegates here, and block states sit in hash maps everywhere, so inheriting would give back every byte saved; Map.hashCode's sum of entry hashes is computed without allocating
    @Override
    public int hashCode() {
        int hash = 0;

        for (int i = 0; i < this.keys.length; i++) {
            Object value = this.values[i];
            hash += this.keys[i].hashCode() ^ (value == null ? 0 : value.hashCode());
        }

        return hash;
    }

    // Same motivation as hashCode: answered from the arrays so no entry set is ever materialised
    @Override
    public boolean equals(Object object) {
        if (object == this) {
            return true;
        }
        // Compared against the Map interface, not this class, since Map.equals must hold across implementations
        if (!(object instanceof Map)) {
            return false;
        }

        Map<?, ?> other = (Map<?, ?>) object;

        // Sizes first: equal sizes plus every one of our entries present in theirs implies the maps match
        if (other.size() != this.keys.length) {
            return false;
        }

        for (int i = 0; i < this.keys.length; i++) {
            Object mine = this.values[i];
            Object theirs = other.get(this.keys[i]);

            // A null on our side needs the containsKey check to tell "mapped to null" from "absent"; a non-null one does not, since get returning null already means unequal
            if (mine == null ? theirs != null || !other.containsKey(this.keys[i]) : !mine.equals(theirs)) {
                return false;
            }
        }

        return true;
    }

    // Same {k=v, k=v} shape AbstractMap produces, built from the arrays so debuggers and crash reports do not force an entry set into existence by printing a state
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
