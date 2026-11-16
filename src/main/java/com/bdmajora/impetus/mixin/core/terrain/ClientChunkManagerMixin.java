package com.bdmajora.impetus.mixin.core.terrain;

import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import com.bdmajora.impetus.engine.impl.render.chunk.map.ChunkStatus;
import com.bdmajora.impetus.engine.impl.render.chunk.map.ChunkTrackerHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes ChunkProviderClient.
@Mixin(ChunkProviderClient.class)
public abstract class ClientChunkManagerMixin {
    // Reference to the field world of the target.
    @Shadow
    @Final
    private World world;

    // Runs before every return of loadChunk.
    @Inject(method = "loadChunk", at = @At("RETURN"))
    private void afterLoadChunkFromPacket(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        ChunkTrackerHolder.get(this.world).onChunkStatusAdded(x, z, ChunkStatus.FLAG_ALL);
    }

    // Runs around the call of Chunk.onUnload in unloadChunk.
    @Inject(method = "unloadChunk", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;onUnload()V", shift = At.Shift.AFTER))
    private void afterUnloadChunk(int x, int z, CallbackInfo ci) {
        ChunkTrackerHolder.get(this.world).onChunkStatusRemoved(x, z, ChunkStatus.FLAG_ALL);
    }
}

