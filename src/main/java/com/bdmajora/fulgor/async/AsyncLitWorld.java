package com.bdmajora.fulgor.async;

import net.minecraft.world.chunk.Chunk;

// Contract of the async lit world.
public interface AsyncLitWorld {
    // Returns the light manager.
    WorldLightManager fulgor$getLightManager();

    // Returns a loaded chunk without loading or generating it.
    Chunk fulgor$getAnyChunkImmediately(int chunkX, int chunkZ);

    // Returns whether chunk pending light.
    boolean fulgor$hasChunkPendingLight(int chunkX, int chunkZ);

    // Shuts down the light manager.
    void fulgor$shutdownLightManager();
}
