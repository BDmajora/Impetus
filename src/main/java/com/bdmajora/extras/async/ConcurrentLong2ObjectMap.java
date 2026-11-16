package com.bdmajora.extras.async;

import it.unimi.dsi.fastutil.longs.AbstractLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Map from longs to values that many threads can use and that refuses null values.
public final class ConcurrentLong2ObjectMap<V> extends AbstractLong2ObjectMap<V> {
    // Entries of the map.
    private final ConcurrentHashMap<Long, V> backing;

    // Creates an empty map sized for the expected entries.
    public ConcurrentLong2ObjectMap(int expected) {
        this.backing = new ConcurrentHashMap<>(expected);
    }

    // Creates a map holding a copy of the entries.
    public ConcurrentLong2ObjectMap(Long2ObjectMap<V> source) {
        this.backing = new ConcurrentHashMap<>(Math.max(16, source.size() * 2));
        for (Long2ObjectMap.Entry<V> entry : source.long2ObjectEntrySet()) {
            this.backing.put(entry.getLongKey(), entry.getValue());
        }
    }

    // Returns the value of the key or the default value.
    @Override
    public V get(long key) {
        V value = this.backing.get(key);
        return value == null ? this.defRetValue : value;
    }

    // Returns whether the key is present.
    @Override
    public boolean containsKey(long key) {
        return this.backing.containsKey(key);
    }

    // Stores the value and returns the previous value or the default value.
    @Override
    public V put(long key, V value) {
        V previous = this.backing.put(key, value);
        return previous == null ? this.defRetValue : previous;
    }

    // Removes the key and returns its value or the default value.
    @Override
    public V remove(long key) {
        V previous = this.backing.remove(key);
        return previous == null ? this.defRetValue : previous;
    }

    // Returns the number of entries.
    @Override
    public int size() {
        return this.backing.size();
    }

    // Returns whether the map holds no entries.
    @Override
    public boolean isEmpty() {
        return this.backing.isEmpty();
    }

    // Removes every entry.
    @Override
    public void clear() {
        this.backing.clear();
    }

    // Returns whether the value is present.
    @Override
    public boolean containsValue(Object value) {
        return this.backing.containsValue(value);
    }

    // Returns a live view of the entries.
    @Override
    public ObjectSet<Long2ObjectMap.Entry<V>> long2ObjectEntrySet() {
        return new AbstractObjectSet<Long2ObjectMap.Entry<V>>() {
            // Returns an iterator over the entries.
            @Override
            public ObjectIterator<Long2ObjectMap.Entry<V>> iterator() {
                Iterator<Map.Entry<Long, V>> source = ConcurrentLong2ObjectMap.this.backing.entrySet().iterator();
                return new ObjectIterator<Long2ObjectMap.Entry<V>>() {
                    // Returns whether another entry remains.
                    @Override
                    public boolean hasNext() {
                        return source.hasNext();
                    }

                    // Returns the next entry.
                    @Override
                    public Long2ObjectMap.Entry<V> next() {
                        Map.Entry<Long, V> entry = source.next();
                        // Creates an entry for the source.
                        return new WriteThroughEntry(entry);
                    }

                    // Removes the entry returned last.
                    @Override
                    public void remove() {
                        source.remove();
                    }

                    // Skips up to the number of entries and returns how many were skipped.
                    @Override
                    public int skip(int n) {
                        int skipped = 0;
                        while (skipped < n && source.hasNext()) {
                            source.next();
                            skipped++;
                        }
                        return skipped;
                    }
                };
            }

            // Returns the number of entries.
            @Override
            public int size() {
                return ConcurrentLong2ObjectMap.this.backing.size();
            }

            // Returns whether the entry is present.
            @Override
            public boolean contains(Object o) {
                if (!(o instanceof Map.Entry)) {
                    return false;
                }
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
                Object key = entry.getKey();
                if (!(key instanceof Long)) {
                    return false;
                }
                V value = ConcurrentLong2ObjectMap.this.backing.get(key);
                return value != null && value.equals(entry.getValue());
            }

            // Removes the entry when it is present.
            @Override
            public boolean remove(Object o) {
                if (!(o instanceof Map.Entry)) {
                    return false;
                }
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
                Object key = entry.getKey();
                return key instanceof Long && ConcurrentLong2ObjectMap.this.backing.remove(key, entry.getValue());
            }
        };
    }

    // Entry that writes its value changes to the map.
    private final class WriteThroughEntry extends BasicEntry<V> {
        // Entry of the backing map.
        private final Map.Entry<Long, V> source;

        WriteThroughEntry(Map.Entry<Long, V> source) {
            super(source.getKey(), source.getValue());
            this.source = source;
        }

        // Stores the value in the map and returns the old value.
        @Override
        public V setValue(V value) {
            V old = this.value;
            this.value = value;
            ConcurrentLong2ObjectMap.this.backing.put(this.key, value);
            return old;
        }
    }
}
