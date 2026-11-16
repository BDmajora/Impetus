package com.bdmajora.coarctatio.dedup;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.concurrent.atomic.LongAdder;

// String pool split into shards that lock separately.
public final class ShardedStringCache {
    // Number of shards.
    private static final int SHARD_COUNT = 16;
    // Mask that selects a shard from a hash.
    private static final int SHARD_MASK = SHARD_COUNT - 1;

    // Name shown in reports.
    private final String name;
    // Largest number of strings in one shard.
    private final int shardSizeLimit;
    // Strings held by each shard.
    private final ObjectOpenHashSet<String>[] shards;

    // Count of lookups that reached a shard with room.
    private final LongAdder requests = new LongAdder();
    // Count of lookups answered by an existing string.
    private final LongAdder hits = new LongAdder();

    // Creates a pool that holds about the size limit of strings.
    @SuppressWarnings("unchecked")
    public ShardedStringCache(String name, int sizeLimit) {
        this.name = name;
        this.shardSizeLimit = Math.max(1, sizeLimit / SHARD_COUNT);
        this.shards = new ObjectOpenHashSet[SHARD_COUNT];

        for (int i = 0; i < SHARD_COUNT; i++) {
            this.shards[i] = new ObjectOpenHashSet<>();
        }
    }

    // Returns the shared instance equal to the string.
    public String deduplicate(String value) {
        if (value == null) {
            return null;
        }

        int hash = value.hashCode();
        hash ^= hash >>> 16;

        ObjectOpenHashSet<String> shard = this.shards[hash & SHARD_MASK];

        synchronized (shard) {
            if (shard.size() >= this.shardSizeLimit) {
                String existing = shard.get(value);
                return existing == null ? value : existing;
            }

            int before = shard.size();
            String existing = shard.addOrGet(value);

            this.requests.increment();
            if (shard.size() == before) {
                this.hits.increment();
            }

            return existing;
        }
    }

    // Empties every shard and resets the counts.
    public void clear() {
        for (ObjectOpenHashSet<String> shard : this.shards) {
            synchronized (shard) {
                shard.clear();
                shard.trim();
            }
        }

        this.requests.reset();
        this.hits.reset();
    }

    // Returns the number of lookups answered by an existing string.
    public long shared() {
        return this.hits.sum();
    }

    // Returns the number of strings held.
    public int size() {
        int total = 0;

        for (ObjectOpenHashSet<String> shard : this.shards) {
            synchronized (shard) {
                total += shard.size();
            }
        }

        return total;
    }

    // Returns a one line report of the pool.
    @Override
    public String toString() {
        long requests = this.requests.sum();

        if (requests == 0) {
            return this.name + " (unused)";
        }

        return String.format("%s (%d/%d shared, %d unique across %d shards)",
                this.name, this.hits.sum(), requests, size(), SHARD_COUNT);
    }
}
