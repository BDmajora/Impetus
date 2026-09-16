package com.bdmajora.equilibrium.mixin.ai.pathfinding_chunk_load;

import com.bdmajora.equilibrium.common.ai.NavigationChunkGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// While a navigation cache is being built on the server, unloaded chunks are taken as absent rather than loaded; ChunkCache already tolerates a null slot (it reads air and treats the region as empty), which is also what the path finder should see for ground that is not there
@Mixin(ChunkCache.class)
public abstract class ChunkCacheMixin {
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getChunk(II)Lnet/minecraft/world/chunk/Chunk;"))
    private Chunk equilibrium$loadedOnlyWhileNavigating(World world, int chunkX, int chunkZ, Operation<Chunk> original) {
        if (!world.isRemote && NavigationChunkGuard.isNavigating()) {
            return world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
        }
        return original.call(world, chunkX, chunkZ);
    }
}
