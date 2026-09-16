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

// Keeps a chunk out of the spawn pass while its initial pass is pending, so peaceful mobs do not appear in caves that are momentarily unlit; matters only when chunks are sent before their light
@Mixin(WorldEntitySpawner.class)
public abstract class WorldEntitySpawnerMixin {
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
