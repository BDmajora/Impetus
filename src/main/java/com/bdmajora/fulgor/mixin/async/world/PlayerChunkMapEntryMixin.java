package com.bdmajora.fulgor.mixin.async.world;

import com.bdmajora.fulgor.FulgorConfig;
import com.bdmajora.fulgor.async.AsyncLitChunk;
import com.bdmajora.fulgor.async.AsyncLitWorld;
import com.bdmajora.fulgor.async.WorldLightManager;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

// Holds a chunk back from clients until its light and its four neighbours' are final; the entry retries every tick so this only delays, and it is not done through Chunk.isPopulated, which also gates block-change packets
@Mixin(PlayerChunkMapEntry.class)
public abstract class PlayerChunkMapEntryMixin {
    @Shadow
    @Nullable
    private Chunk chunk;

    @Shadow
    private boolean sentToPlayers;

    @Inject(method = "sendToPlayers", at = @At("HEAD"), cancellable = true)
    private void fulgor$gateSendOnLight(CallbackInfoReturnable<Boolean> cir) {
        // Already sent (a relight only resends through the manager), or nothing to gate yet
        if (this.sentToPlayers || FulgorConfig.get().asyncSendChunksWithoutLight) {
            return;
        }
        Chunk c = this.chunk;
        if (c == null) {
            return;
        }
        if (!((AsyncLitChunk) c).fulgor$isLightReady()) {
            cir.setReturnValue(false);
            return;
        }
        WorldLightManager manager = ((AsyncLitWorld) c.getWorld()).fulgor$getLightManager();
        if (manager != null && !manager.areNeighboursLightReady(c.x, c.z)) {
            cir.setReturnValue(false);
        }
    }
}
