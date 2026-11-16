package com.bdmajora.equilibrium.mixin.world.inline_block_access;

import com.bdmajora.equilibrium.common.world.ChunkAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

// Mixin that changes World.
@Mixin(World.class)
public abstract class WorldMixin implements ChunkAccess {
    // Replaces the whole method getChunk.
    @Overwrite
    public Chunk getChunk(int chunkX, int chunkZ) {
        return this.equilibrium$getChunkCached(chunkX, chunkZ);
    }
}
