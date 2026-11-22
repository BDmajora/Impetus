package com.bdmajora.impetus.mixin.core.simulation;

import com.bdmajora.impetus.impl.world.SimulationDistance;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldEntitySpawner;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

@Mixin(WorldEntitySpawner.class)
public abstract class WorldEntitySpawnerMixin {
    // Vanilla's "!eligible.contains(pos)" guards both the mob-cap chunk count (++i) and eligibility, so answering "already seen"
    // for a chunk outside the simulation distance drops it from both. Gating only eligibility (isSentToPlayers) kept the full 17x17
    // count per player, which packed the whole cap into the smaller simulated square: up to ~2.4x vanilla density at distance 5.
    // Modern spawning also sizes the cap from the ticking chunks only.
    @WrapOperation(method = "findChunksForSpawning", at = @At(value = "INVOKE",
            target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z", remap = false))
    private boolean impetus$skipUnsimulatedChunks(Set<ChunkPos> eligible, Object pos, Operation<Boolean> original,
                                                  @Local(argsOnly = true) WorldServer world) {
        ChunkPos chunk = (ChunkPos) pos;
        return !SimulationDistance.shouldTick(world, chunk.x, chunk.z) || original.call(eligible, pos);
    }
}
