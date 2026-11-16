package com.bdmajora.fulgor.mixin.network;

import com.bdmajora.fulgor.api.LightingEngineProvider;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Mixin that changes SPacketChunkData.
@Mixin(SPacketChunkData.class)
public abstract class SPacketChunkDataMixin {
    // Runs at the start of calculateChunkSize.
    @Inject(method = "calculateChunkSize", at = @At("HEAD"))
    private void fulgor$flushBeforeSerialize(Chunk chunk, boolean hasSkyLight, int changedSectionFilter,
                                             CallbackInfoReturnable<Integer> cir) {
        ((LightingEngineProvider) chunk).fulgor$getLightingEngine().processLightUpdates();
    }
}
