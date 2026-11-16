package com.bdmajora.fulgor.mixin.async.world;

import com.bdmajora.fulgor.async.AsyncLitWorld;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.world.WorldEntitySpawner;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes WorldEntitySpawner.
@Mixin(WorldEntitySpawner.class)
public abstract class WorldEntitySpawnerMixin {
    // Wraps the call of PlayerChunkMapEntry.isSentToPlayers in findChunksForSpawning.
    @WrapOperation(method = "findChunksForSpawning",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/management/PlayerChunkMapEntry;isSentToPlayers()Z"))
    private boolean fulgor$skipPendingLightChunks(PlayerChunkMapEntry entry, Operation<Boolean> original,
                                                  @Local(argsOnly = true) WorldServer world) {
        if (!original.call(entry)) {
            return false;
        }
        return !((AsyncLitWorld) world).fulgor$hasChunkPendingLight(entry.getPos().x, entry.getPos().z);
    }
}
