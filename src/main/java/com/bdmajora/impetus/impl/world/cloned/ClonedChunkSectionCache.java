package com.bdmajora.impetus.impl.world.cloned;

import it.unimi.dsi.fastutil.longs.Long2ReferenceLinkedOpenHashMap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;

// Cache of cloned chunk sections kept in order of use with a size and age limit.
public class ClonedChunkSectionCache {
    // Largest number of cached sections.
    private static final int MAX_CACHE_SIZE = 512;
    // Age in nanoseconds after which an unused section expires.
    private static final long MAX_CACHE_DURATION = TimeUnit.SECONDS.toNanos(5);

    // World the sections come from.
    private final World world;

    // Sections by packed position from least to most recently used.
    private final Long2ReferenceLinkedOpenHashMap<ClonedChunkSection> positionToEntry = new Long2ReferenceLinkedOpenHashMap<>();

    // Time of the last cleanup in nanoseconds.
    private long time;

    // Creates an empty cache for a world.
    public ClonedChunkSectionCache(World world) {
        this.world = world;
        this.time = getMonotonicTimeSource();
    }

    // Removes the sections that were not used within the age limit.
    public synchronized void cleanup() {
        this.time = getMonotonicTimeSource();
        long now = this.time;
        // Drops entries unused for longer than the cache duration.
        this.positionToEntry.values().removeIf(entry -> now - entry.getLastUsedTimestamp() > MAX_CACHE_DURATION);
    }

    // Returns the cached section or clones it and evicts the least recently used past the size limit.
    @Nullable
    public synchronized ClonedChunkSection acquire(int x, int y, int z) {
        var pos = PositionUtil.packSection(x, y, z);
        var section = this.positionToEntry.getAndMoveToLast(pos);

        if (section == null) {
            section = this.clone(x, y, z);

            while (this.positionToEntry.size() >= MAX_CACHE_SIZE) {
                this.positionToEntry.removeFirst();
            }

            this.positionToEntry.putAndMoveToLast(pos, section);
        }

        section.setLastUsedTimestamp(this.time);

        return section;
    }

    // Clones a section and rejects an unloaded chunk.
    @NotNull
    private ClonedChunkSection clone(int x, int y, int z) {
        Chunk chunk = this.world.getChunk(x, z);

        if (chunk == null) {
            throw new RuntimeException("Chunk is not loaded");
        }

        return new ClonedChunkSection(this.world, chunk, x, y, z);
    }

    // Forgets the section at a position.
    public synchronized void invalidate(int x, int y, int z) {
        this.positionToEntry.remove(PositionUtil.packSection(x, y, z));
    }

    // Returns the monotonic time in nanoseconds.
    private static long getMonotonicTimeSource() {
        return System.nanoTime();
    }
}
