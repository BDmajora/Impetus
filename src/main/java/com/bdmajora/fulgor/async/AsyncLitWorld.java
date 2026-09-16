package com.bdmajora.fulgor.async;

import net.minecraft.world.chunk.Chunk;

// Implemented by World under the async engine
public interface AsyncLitWorld {
    // The world's manager, created on first use; null for a world the engine refuses (JEI / GregTech preview worlds)
    WorldLightManager fulgor$getLightManager();

    // The chunk if registered with the manager, never loading one; safe from any thread
    Chunk fulgor$getAnyChunkImmediately(int chunkX, int chunkZ);

    // Whether a registered chunk's initial pass is still pending
    boolean fulgor$hasChunkPendingLight(int chunkX, int chunkZ);

    void fulgor$shutdownLightManager();
}
