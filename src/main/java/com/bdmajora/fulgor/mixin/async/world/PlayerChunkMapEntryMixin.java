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

// Mixin that changes PlayerChunkMapEntry.
@Mixin(PlayerChunkMapEntry.class)
public abstract class PlayerChunkMapEntryMixin {
    // Reference to the field chunk of the target.
    @Shadow
    @Nullable
    private Chunk chunk;

    // Reference to the field sent to players of the target.
    @Shadow
    private boolean sentToPlayers;

    // Runs at the start of sendToPlayers and can cancel it.
    @Inject(method = "sendToPlayers", at = @At("HEAD"), cancellable = true)
    private void fulgor$gateSendOnLight(CallbackInfoReturnable<Boolean> cir) {
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
