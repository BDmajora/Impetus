package com.bdmajora.coarctatio.dedup;

import it.unimi.dsi.fastutil.Hash;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

// Interning pool mapping equal values onto the first instance seen (from Hydrogen, plus a size cap and closing for bake-scoped pools); hits take no lock, since a few dozen face UVs hammered from every model-reading thread turned one monitor into a convoy as slow as a single thread
public class DeduplicationCache<T> {
    // Only used for toString, i.e. the memory report line
    private final String name;
    // How values are hashed and compared; null keys values by their own equals, and the quad pool passes an array strategy so int[] contents are compared rather than references
    private final Hash.Strategy<T> strategy;
    private final int sizeLimit;

    // Null once closed, which is also how deduplicate knows to stop pooling; keyed by the value itself, or by a StrategyKey around it
    private volatile ConcurrentHashMap<Object, T> pool;

    // Counters for the memory report; requests includes hits
    private final LongAdder requests = new LongAdder();
    private final LongAdder hits = new LongAdder();
    private volatile boolean saturated;

    // Pool size captured at close(), so the statistics survive the backing map being dropped
    private volatile int retainedSize;

    // Values compared by their own hashCode and equals
    public DeduplicationCache(String name, int sizeLimit) {
        this(name, sizeLimit, null);
    }

    public DeduplicationCache(String name, int sizeLimit, Hash.Strategy<T> strategy) {
        this.name = name;
        this.sizeLimit = sizeLimit;
        this.strategy = strategy;
        this.pool = new ConcurrentHashMap<>();
    }

    // Returns the canonical instance for value (possibly value itself); null and a closed pool hand the argument straight back so callers need no checks
    public T deduplicate(T value) {
        ConcurrentHashMap<Object, T> pool = this.pool;
        if (value == null || pool == null) {
            return value;
        }

        this.requests.increment();
        Object key = this.strategy == null ? value : new StrategyKey<>(value, this.strategy);

        // Scored by whether the pool grew, not by instance identity: callers often hand back an instance the pool issued (copying an NBT compound re-inserts interned keys), and those are shares too
        T existing = pool.get(key);
        if (existing == null) {
            existing = pool.putIfAbsent(key, value);
        }
        if (existing != null) {
            this.hits.increment();
            return existing;
        }

        // Flagged after the insert so a saturated pool still serves hits for everything it knows
        if (pool.size() >= this.sizeLimit) {
            this.saturated = true;
        }

        return value;
    }

    // Re-opens with a fresh backing map and reset counters, so the memory report describes this bake rather than every bake since launch
    public synchronized void open() {
        this.requests.reset();
        this.hits.reset();
        this.saturated = false;
        this.retainedSize = 0;
        this.pool = new ConcurrentHashMap<>();
    }

    // Drops the backing map; canonicalised values stay shared, later deduplicate calls return their argument, and the size is copied first for the memory report
    public synchronized void close() {
        ConcurrentHashMap<Object, T> pool = this.pool;
        if (pool != null) {
            this.retainedSize = pool.size();
            this.pool = null;
        }
    }

    // Lookups that found an existing entry, i.e. the number of objects this pool kept from being allocated
    public long shared() {
        return this.hits.sum();
    }

    // Entries pooled: the live count while open, the count captured at close afterwards
    public int size() {
        ConcurrentHashMap<Object, T> pool = this.pool;
        return pool == null ? this.retainedSize : pool.size();
    }

    // The memory report line; "unused" is distinct from "0 shared" because a pool nothing asked about usually means its injection never fired
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

    // A value hashed and compared through the pool's strategy, with the hash computed once
    private static final class StrategyKey<T> {
        private final T value;
        private final Hash.Strategy<T> strategy;
        private final int hash;

        StrategyKey(T value, Hash.Strategy<T> strategy) {
            this.value = value;
            this.strategy = strategy;
            this.hash = strategy.hashCode(value);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean equals(Object other) {
            return other instanceof StrategyKey && this.strategy.equals(this.value, ((StrategyKey<T>) other).value);
        }
    }
}
