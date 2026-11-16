package com.bdmajora.impetus.mixin.core.terrain;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Mixin that changes ChunkProviderClient.
@Mixin(ChunkProviderClient.class)
public interface ChunkProviderClientAccessor {
    // Gives access to the field get loaded chunks.
    @Accessor("loadedChunks")
    Long2ObjectMap<Chunk> impetus$getLoadedChunks();
}
