package com.bdmajora.equilibrium.mixin.ai.pathfinding_chunk_load;

import com.bdmajora.equilibrium.common.ai.NavigationChunkGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes ChunkCache.
@Mixin(ChunkCache.class)
public abstract class ChunkCacheMixin {
    // Wraps the call of World.getChunk in constructor.
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getChunk(II)Lnet/minecraft/world/chunk/Chunk;"))
    private Chunk equilibrium$loadedOnlyWhileNavigating(World world, int chunkX, int chunkZ, Operation<Chunk> original) {
        if (!world.isRemote && NavigationChunkGuard.isNavigating()) {
            return world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
        }
        return original.call(world, chunkX, chunkZ);
    }
}
