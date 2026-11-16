package com.bdmajora.coarctatio.collections;

import com.google.common.cache.AbstractCache;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

// Cache split into one small cache per parent so a parent and its entries can be reclaimed together.
public final class ParentPartitionedCache<P, K, V> extends AbstractCache<K, V> {
    // Partition of each parent matched by identity and held by weak keys.
    private final LoadingCache<P, Cache<K, V>> byParent;
    // Partition of the keys without a parent.
    private final Cache<K, V> orphans;
    // Function that returns the parent of a key or null.
    private final Function<? super K, ? extends P> parentOf;

    // Creates a cache with limits on the parents and on the entries of each partition and rejects a null function or a negative limit.
    public ParentPartitionedCache(Function<? super K, ? extends P> parentOf, int maxParents, int maxEntriesPerParent, Duration expiry) {
        this.parentOf = Objects.requireNonNull(parentOf, "parentOf");
        Objects.requireNonNull(expiry, "expiry");
        if (maxParents < 0 || maxEntriesPerParent < 0) {
            throw new IllegalArgumentException("Negative cache limit " + maxParents + " " + maxEntriesPerParent);
        }
        this.byParent = CacheBuilder.newBuilder()
                .maximumSize(maxParents)
                .expireAfterWrite(expiry)
                .weakKeys()
                .softValues()
                .recordStats()
                .build(new CacheLoader<P, Cache<K, V>>() {
                    // Creates the partition of a parent.
                    @Override
                    public Cache<K, V> load(P parent) {
                        return partition(maxEntriesPerParent, expiry);
                    }
                });
        this.orphans = partition(maxEntriesPerParent, expiry);
    }

    // Returns an empty partition with the entry limit and expiry.
    private static <K, V> Cache<K, V> partition(int maxEntries, Duration expiry) {
        return CacheBuilder.newBuilder().maximumSize(maxEntries).expireAfterWrite(expiry).recordStats().build();
    }

    // Returns the partition of a key and creates it when missing.
    private Cache<K, V> partitionFor(K key) {
        P parent = this.parentOf.apply(key);
        return parent == null ? this.orphans : this.byParent.getUnchecked(parent);
    }

    // Returns the partition of a key without creating it or null.
    @Nullable
    private Cache<K, V> existingPartitionFor(Object key) {
        @SuppressWarnings("unchecked")
        P parent = this.parentOf.apply((K) key);
        return parent == null ? this.orphans : this.byParent.getIfPresent(parent);
    }

    // Returns the cached value of a key or null.
    @Nullable
    @Override
    public V getIfPresent(Object key) {
        Objects.requireNonNull(key, "key");
        Cache<K, V> partition = existingPartitionFor(key);
        return partition == null ? null : partition.getIfPresent(key);
    }

    // Returns the cached value of a key or loads and stores it.
    @Override
    public V get(K key, Callable<? extends V> loader) throws ExecutionException {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(loader, "loader");
        return partitionFor(key).get(key, loader);
    }

    // Returns the cached values of the keys that are present.
    @Override
    public ImmutableMap<K, V> getAllPresent(Iterable<?> keys) {
        ImmutableMap.Builder<K, V> present = ImmutableMap.builder();
        // Keys already added so a repeated key is added once
        Set<Object> seen = new HashSet<>();
        for (Object key : keys) {
            V value = getIfPresent(key);
            if (value != null && seen.add(key)) {
                @SuppressWarnings("unchecked")
                K typed = (K) key;
                present.put(typed, value);
            }
        }
        return present.build();
    }

    // Stores a value under a key.
    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        partitionFor(key).put(key, value);
    }

    // Removes the value of a key.
    @Override
    public void invalidate(Object key) {
        Objects.requireNonNull(key, "key");
        Cache<K, V> partition = existingPartitionFor(key);
        if (partition != null) {
            partition.invalidate(key);
        }
    }

    // Removes every value.
    @Override
    public void invalidateAll() {
        this.byParent.invalidateAll();
        this.orphans.invalidateAll();
    }

    // Returns the number of values in every partition.
    @Override
    public long size() {
        long total = this.orphans.size();
        for (Cache<K, V> partition : this.byParent.asMap().values()) {
            total += partition.size();
        }
        return total;
    }

    // Returns the summed statistics of every partition.
    @Override
    public CacheStats stats() {
        CacheStats total = this.orphans.stats();
        for (Cache<K, V> partition : this.byParent.asMap().values()) {
            total = total.plus(partition.stats());
        }
        return total;
    }

    // Returns a copy of every value by key.
    @Override
    public ConcurrentMap<K, V> asMap() {
        ConcurrentMap<K, V> copy = new ConcurrentHashMap<>(this.orphans.asMap());
        for (Cache<K, V> partition : this.byParent.asMap().values()) {
            copy.putAll(partition.asMap());
        }
        return copy;
    }

    // Runs pending maintenance of every partition.
    @Override
    public void cleanUp() {
        this.byParent.cleanUp();
        this.orphans.cleanUp();
        for (Cache<K, V> partition : this.byParent.asMap().values()) {
            partition.cleanUp();
        }
    }

    // Returns the number of parents with a partition.
    public long parentCount() {
        return this.byParent.size();
    }
}
