package com.bdmajora.coarctatio.collections;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

// Read only map that keeps its entries in two arrays in insertion order.
public class ArrayBackedLinkedMap<K, V> extends AbstractMap<K, V> {
    // Keys in insertion order.
    private final K[] keys;
    // Values in the order of the keys.
    private final V[] values;

    // Creates a map holding a copy of the source entries.
    @SuppressWarnings("unchecked")
    public ArrayBackedLinkedMap(Map<K, V> src) {
        int size = src.size();

        this.keys = (K[]) new Object[size];
        this.values = (V[]) new Object[size];

        int i = 0;
        for (Map.Entry<K, V> entry : src.entrySet()) {
            this.keys[i] = entry.getKey();
            this.values[i] = entry.getValue();
            i++;
        }
    }

    // Returns the number of entries.
    @Override
    public int size() {
        return this.keys.length;
    }

    // Returns whether the map holds no entries.
    @Override
    public boolean isEmpty() {
        return this.keys.length == 0;
    }

    // Returns whether the key is present.
    @Override
    public boolean containsKey(Object key) {
        return indexOf(key) >= 0;
    }

    // Returns the value of the key or null when absent.
    @Override
    public V get(Object key) {
        int index = indexOf(key);
        return index < 0 ? null : this.values[index];
    }

    // Returns the position of the key or minus one when absent.
    private int indexOf(Object key) {
        K[] keys = this.keys;

        for (int i = 0; i < keys.length; i++) {
            if (Objects.equals(keys[i], key)) {
                return i;
            }
        }

        return -1;
    }

    // Rejects modification.
    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    // Rejects modification.
    @Override
    public V remove(Object key) {
        throw new UnsupportedOperationException();
    }

    // Rejects modification.
    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    // Returns a read only view of the entries.
    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    // Entry view over the arrays.
    private final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
        // Returns an iterator over the entries in insertion order.
        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return new Iterator<Map.Entry<K, V>>() {
                private int index;

                @Override
                public boolean hasNext() {
                    return this.index < ArrayBackedLinkedMap.this.keys.length;
                }

                @Override
                public Map.Entry<K, V> next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }

                    int i = this.index++;
                    return new SimpleImmutableEntry<>(ArrayBackedLinkedMap.this.keys[i],
                            ArrayBackedLinkedMap.this.values[i]);
                }
            };
        }

        // Returns the number of entries.
        @Override
        public int size() {
            return ArrayBackedLinkedMap.this.keys.length;
        }
    }
}
