package com.bdmajora.extras.client.culling;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

// Block changes the main thread queues for the worker's grid.
public final class OcclusionInbox {
    // Chunks loaded or unloaded since the last drain with null for an unloaded chunk.
    private Long2ObjectOpenHashMap<Chunk> chunks = new Long2ObjectOpenHashMap<>();
    // Masks of changed sections by chunk key.
    private Long2IntOpenHashMap sections = new Long2IntOpenHashMap();
    // Every loaded chunk of a new world or null when no reset is queued.
    private Long2ObjectOpenHashMap<Chunk> reset;

    // Changes the worker takes in one go.
    public record Batch(Long2ObjectOpenHashMap<Chunk> reset, Long2ObjectOpenHashMap<Chunk> chunks, Long2IntOpenHashMap sections) {
    }

    // Queues a chunk that was loaded or replaced or unloaded.
    public synchronized void chunk(int chunkX, int chunkZ, Chunk chunk) {
        this.chunks.put(ChunkPos.asLong(chunkX, chunkZ), chunk);
    }

    // Queues a section whose opaque blocks changed.
    public synchronized void section(int chunkX, int sectionY, int chunkZ) {
        if (sectionY < 0 || sectionY >= OcclusionGrid.SECTIONS) {
            return;
        }
        long key = ChunkPos.asLong(chunkX, chunkZ);
        this.sections.put(key, this.sections.get(key) | (1 << sectionY));
    }

    // Replaces every queued change with the loaded chunks of a new world.
    public synchronized void reset(Long2ObjectOpenHashMap<Chunk> loaded) {
        this.reset = loaded;
        this.chunks.clear();
        this.sections.clear();
    }

    // Takes every queued change or returns null when nothing is queued.
    public synchronized Batch drain() {
        if (this.reset == null && this.chunks.isEmpty() && this.sections.isEmpty()) {
            return null;
        }
        Batch batch = new Batch(this.reset, this.chunks, this.sections);
        this.reset = null;
        this.chunks = new Long2ObjectOpenHashMap<>();
        this.sections = new Long2IntOpenHashMap();
        return batch;
    }
}
