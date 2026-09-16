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

// Flags the ChunkCache a path search snapshots the world into, so ChunkCacheMixin can stop it loading chunks (Universal Tweaks' "No Pathfinding Chunk Loading"); a mob at a chunk border otherwise loads and generates its neighbours just by looking for a route
@Mixin(PathNavigate.class)
public abstract class PathNavigateMixin {
    @WrapOperation(method = {"getPathToPos", "getPathToEntityLiving"}, at = @At(value = "NEW", target = "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/BlockPos;I)Lnet/minecraft/world/ChunkCache;"))
    private ChunkCache equilibrium$flagNavigationCache(World world, BlockPos from, BlockPos to, int sub, Operation<ChunkCache> original) {
        NavigationChunkGuard.set(true);
        try {
            return original.call(world, from, to, sub);
        } finally {
            NavigationChunkGuard.set(false);
        }
    }
}
