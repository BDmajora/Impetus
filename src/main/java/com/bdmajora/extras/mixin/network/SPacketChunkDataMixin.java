package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.play.server.SPacketChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes SPacketChunkData.
@Mixin(SPacketChunkData.class)
public abstract class SPacketChunkDataMixin {
    @ModifyExpressionValue(method = "readPacketData",
            at = @At(value = "CONSTANT", args = "intValue=" + NetworkLimits.VANILLA_CHUNK_DATA), require = 0)
    private int impetus$chunkDataLimit(int limit) {
        return limit == NetworkLimits.VANILLA_CHUNK_DATA ? NetworkLimits.chunkDataLimit() : limit;
    }
}
