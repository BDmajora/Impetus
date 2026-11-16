package com.bdmajora.equilibrium.mixin.util.chunk_access;

import com.bdmajora.equilibrium.common.world.ChunkAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nullable;

// Mixin that changes World.
@Mixin(World.class)
public abstract class WorldMixin implements ChunkAccess {
    // Reference to the field chunk provider of the target.
    @Shadow
    protected IChunkProvider chunkProvider;

    // Field last chunk added to the target.
    @Unique
    private Chunk equilibrium$lastChunk;

    // Returns the loaded chunk.
    @Nullable
    @Override
    public Chunk equilibrium$getLoadedChunk(int chunkX, int chunkZ) {
        Chunk cached = this.equilibrium$lastChunk;

        if (cached != null && cached.x == chunkX && cached.z == chunkZ && cached.isLoaded()) {
            cached.unloadQueued = false;
            return cached;
        }

        Chunk chunk = this.chunkProvider.getLoadedChunk(chunkX, chunkZ);

        if (chunk != null) {
            this.equilibrium$lastChunk = chunk;
        }

        return chunk;
    }

    // Returns the chunk cached.
    @Override
    public Chunk equilibrium$getChunkCached(int chunkX, int chunkZ) {
        Chunk cached = this.equilibrium$lastChunk;

        if (cached != null && cached.x == chunkX && cached.z == chunkZ && cached.isLoaded()) {
            cached.unloadQueued = false;
            return cached;
        }

        Chunk chunk = this.chunkProvider.provideChunk(chunkX, chunkZ);

        if (chunk != null && chunk.x == chunkX && chunk.z == chunkZ) {
            this.equilibrium$lastChunk = chunk;
        }

        return chunk;
    }
}
