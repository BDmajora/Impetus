package com.bdmajora.fulgor.async;

import net.minecraft.world.chunk.Chunk;

import java.util.concurrent.ConcurrentHashMap;

// The chunks the engine may read, keyed by ChunkPos.asLong; a ConcurrentHashMap so the workers' lookups are lock-free and a put on the main thread happens-before the worker's get
final class LoadedChunkMap {
    private final ConcurrentHashMap<Long, Chunk> map = new ConcurrentHashMap<>(256);

    void put(long key, Chunk value) {
        this.map.put(key, value);
    }

    Chunk remove(long key) {
        return this.map.remove(key);
    }

    Chunk get(long key) {
        return this.map.get(key);
    }
}
