package com.bdmajora.coarctatio.dedup;

import it.unimi.dsi.fastutil.Hash;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

// Pool that hands out one shared instance per equal value.
public class DeduplicationCache<T> {
    // Name shown in reports.
    private final String name;
    // Equality rule or null for natural equality.
    private final Hash.Strategy<T> strategy;
    // Largest number of values the pool keeps.
    private final int sizeLimit;

    // Values kept by the pool or null while closed.
    private volatile ConcurrentHashMap<Object, T> pool;

    // Count of lookups.
    private final LongAdder requests = new LongAdder();
    // Count of lookups answered by an existing value.
    private final LongAdder hits = new LongAdder();
    // Saturated state of the pool.
    private volatile boolean saturated;

    // Size recorded when the pool was closed.
    private volatile int retainedSize;

    // Creates a pool that uses natural equality.
    public DeduplicationCache(String name, int sizeLimit) {
        this(name, sizeLimit, null);
    }

    // Creates a pool that uses the equality rule.
    public DeduplicationCache(String name, int sizeLimit, Hash.Strategy<T> strategy) {
        this.name = name;
        this.sizeLimit = sizeLimit;
        this.strategy = strategy;
        this.pool = new ConcurrentHashMap<>();
    }

    // Returns the shared instance equal to the value and keeps the value when none exists and the pool has room.
    public T deduplicate(T value) {
        ConcurrentHashMap<Object, T> pool = this.pool;
        if (value == null || pool == null) {
            return value;
        }

        this.requests.increment();
        Object key = this.strategy == null ? value : new StrategyKey<>(value, this.strategy);

        T existing = pool.get(key);
        if (existing == null) {
            if (pool.size() >= this.sizeLimit) {
                this.saturated = true;
                return value;
            }
            existing = pool.putIfAbsent(key, value);
        }
        if (existing != null) {
            this.hits.increment();
            return existing;
        }

        if (pool.size() >= this.sizeLimit) {
            this.saturated = true;
        }

        return value;
    }

    // Empties the pool and starts accepting values.
    public synchronized void open() {
        this.requests.reset();
        this.hits.reset();
        this.saturated = false;
        this.retainedSize = 0;
        this.pool = new ConcurrentHashMap<>();
    }

    // Drops the pool and remembers its size.
    public synchronized void close() {
        ConcurrentHashMap<Object, T> pool = this.pool;
        if (pool != null) {
            this.retainedSize = pool.size();
            this.pool = null;
        }
    }

    // Returns the number of lookups answered by an existing value.
    public long shared() {
        return this.hits.sum();
    }

    // Returns the number of values held or held at close.
    public int size() {
        ConcurrentHashMap<Object, T> pool = this.pool;
        return pool == null ? this.retainedSize : pool.size();
    }

    // Returns a one line report of the pool.
    @Override
    public String toString() {
        long requests = this.requests.sum();
        if (requests == 0) {
            return this.name + " (unused)";
        }

        return String.format("%s (%d/%d shared, %d unique%s%s)",
                this.name, this.hits.sum(), requests, size(),
                this.saturated ? ", saturated" : "",
                this.pool == null ? ", closed" : "");
    }

    // Key that applies an equality rule to a value.
    private static final class StrategyKey<T> {
        // Wrapped value.
        private final T value;
        // Rule used for equality and hashing.
        private final Hash.Strategy<T> strategy;
        // Hash computed once at construction.
        private final int hash;

        // Creates a key for the value.
        StrategyKey(T value, Hash.Strategy<T> strategy) {
            this.value = value;
            this.strategy = strategy;
            this.hash = strategy.hashCode(value);
        }

        // Returns the stored hash.
        @Override
        public int hashCode() {
            return this.hash;
        }

        // Returns whether the other key holds an equal value.
        @Override
        @SuppressWarnings("unchecked")
        public boolean equals(Object other) {
            return other instanceof StrategyKey && this.strategy.equals(this.value, ((StrategyKey<T>) other).value);
        }
    }
}
