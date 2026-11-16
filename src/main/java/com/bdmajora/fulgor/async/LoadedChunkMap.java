package com.bdmajora.fulgor.async;

import net.minecraft.world.chunk.Chunk;

import java.util.concurrent.ConcurrentHashMap;

// Map of loaded chunks by packed position.
final class LoadedChunkMap {
    private final ConcurrentHashMap<Long, Chunk> map = new ConcurrentHashMap<>(256);

    // Stores a chunk under its key.
    void put(long key, Chunk value) {
        this.map.put(key, value);
    }

    // Removes and returns the chunk of a key.
    Chunk remove(long key) {
        return this.map.remove(key);
    }

    // Returns the value.
    Chunk get(long key) {
        return this.map.get(key);
    }
}
