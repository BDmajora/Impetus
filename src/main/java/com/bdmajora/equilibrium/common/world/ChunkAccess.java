package com.bdmajora.equilibrium.common.world;

import net.minecraft.world.chunk.Chunk;

import javax.annotation.Nullable;

// World that exposes chunk lookups.
public interface ChunkAccess {
    // Returns the loaded chunk or null.
    @Nullable
    Chunk equilibrium$getLoadedChunk(int chunkX, int chunkZ);

    // Returns the chunk and loads it when needed.
    Chunk equilibrium$getChunkCached(int chunkX, int chunkZ);
}
