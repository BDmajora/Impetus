package com.bdmajora.impetus.mixin.core.simulation;

import com.bdmajora.impetus.impl.world.SimulationDistance;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldEntitySpawner;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldEntitySpawner.class)
public abstract class WorldEntitySpawnerMixin {
    @WrapOperation(method = "findChunksForSpawning", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/management/PlayerChunkMapEntry;isSentToPlayers()Z"))
    private boolean impetus$limitNaturalSpawning(PlayerChunkMapEntry entry, Operation<Boolean> original,
                                                 @Local(argsOnly = true) WorldServer world) {
        ChunkPos pos = entry.getPos();
        return SimulationDistance.shouldTick(world, pos.x, pos.z) && original.call(entry);
    }
}
