package com.bdmajora.extras.async;

import it.unimi.dsi.fastutil.longs.AbstractLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Long2ObjectMap over a ConcurrentHashMap, standing in for ChunkProviderServer.loadedChunks: worker-side block reads hit get() while the main thread loads and unloads, and the open-hash map corrupts under that; boxing the key costs a little on every access but the iteration views stay weakly consistent instead of throwing
public final class ConcurrentLong2ObjectMap<V> extends AbstractLong2ObjectMap<V> {
    private final ConcurrentHashMap<Long, V> backing;

    public ConcurrentLong2ObjectMap(int expected) {
        this.backing = new ConcurrentHashMap<>(expected);
    }

    public ConcurrentLong2ObjectMap(Long2ObjectMap<V> source) {
        this.backing = new ConcurrentHashMap<>(Math.max(16, source.size() * 2));
        for (Long2ObjectMap.Entry<V> entry : source.long2ObjectEntrySet()) {
            this.backing.put(entry.getLongKey(), entry.getValue());
        }
    }

    @Override
    public V get(long key) {
        V value = this.backing.get(key);
        return value == null ? this.defRetValue : value;
    }

    @Override
    public boolean containsKey(long key) {
        return this.backing.containsKey(key);
    }

    @Override
    public V put(long key, V value) {
        V previous = this.backing.put(key, value);
        return previous == null ? this.defRetValue : previous;
    }

    @Override
    public V remove(long key) {
        V previous = this.backing.remove(key);
        return previous == null ? this.defRetValue : previous;
    }

    @Override
    public int size() {
        return this.backing.size();
    }

    @Override
    public boolean isEmpty() {
        return this.backing.isEmpty();
    }

    @Override
    public void clear() {
        this.backing.clear();
    }

    @Override
    public boolean containsValue(Object value) {
        return this.backing.containsValue(value);
    }

    // Entries are materialised per step; setValue writes through so the fastutil contract holds
    @Override
    public ObjectSet<Long2ObjectMap.Entry<V>> long2ObjectEntrySet() {
        return new AbstractObjectSet<Long2ObjectMap.Entry<V>>() {
            @Override
            public ObjectIterator<Long2ObjectMap.Entry<V>> iterator() {
                Iterator<Map.Entry<Long, V>> source = ConcurrentLong2ObjectMap.this.backing.entrySet().iterator();
                return new ObjectIterator<Long2ObjectMap.Entry<V>>() {
                    @Override
                    public boolean hasNext() {
                        return source.hasNext();
                    }

                    @Override
                    public Long2ObjectMap.Entry<V> next() {
                        Map.Entry<Long, V> entry = source.next();
                        return new WriteThroughEntry(entry);
                    }

                    @Override
                    public void remove() {
                        source.remove();
                    }

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

            @Override
            public int size() {
                return ConcurrentLong2ObjectMap.this.backing.size();
            }

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

    private final class WriteThroughEntry extends BasicEntry<V> {
        private final Map.Entry<Long, V> source;

        WriteThroughEntry(Map.Entry<Long, V> source) {
            super(source.getKey(), source.getValue());
            this.source = source;
        }

        @Override
        public V setValue(V value) {
            V old = this.value;
            this.value = value;
            ConcurrentLong2ObjectMap.this.backing.put(this.key, value);
            return old;
        }
    }
}
