package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelProcessor;
import com.bdmajora.extras.async.ParallelSpawner;
import net.minecraft.world.WorldEntitySpawner;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes WorldEntitySpawner.
@Mixin(WorldEntitySpawner.class)
public abstract class WorldEntitySpawnerParallelMixin {
    // Runs at the start of findChunksForSpawning and can cancel it.
    @Inject(method = "findChunksForSpawning", at = @At("HEAD"), cancellable = true)
    private void impetus$parallelSpawn(WorldServer world, boolean spawnHostile, boolean spawnPeaceful, boolean spawnAnimals, CallbackInfoReturnable<Integer> cir) {
        if (ParallelProcessor.isSpawningActive(world)) {
            cir.setReturnValue(ParallelSpawner.findChunksForSpawning(world, spawnHostile, spawnPeaceful, spawnAnimals));
        }
    }
}
