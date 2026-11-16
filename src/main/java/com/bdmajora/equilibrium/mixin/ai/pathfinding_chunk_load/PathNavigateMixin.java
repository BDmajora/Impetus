package com.bdmajora.equilibrium.mixin.ai.pathfinding_chunk_load;

import com.bdmajora.equilibrium.common.ai.NavigationChunkGuard;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes PathNavigate.
@Mixin(PathNavigate.class)
public abstract class PathNavigateMixin {
    // Flags chunk loads as navigation while a path search builds its chunk cache.
    @WrapOperation(method = {"getPathToPos", "getPathToEntityLiving"}, at = @At(value = "NEW", target = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/BlockPos;I)Lnet/minecraft/world/ChunkCache;", remap = false))
    private ChunkCache equilibrium$flagNavigationCache(World world, BlockPos from, BlockPos to, int sub, Operation<ChunkCache> original) {
        NavigationChunkGuard.set(true);
        try {
            return original.call(world, from, to, sub);
        } finally {
            NavigationChunkGuard.set(false);
        }
    }
}
