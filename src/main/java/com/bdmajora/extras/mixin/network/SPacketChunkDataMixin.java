package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import net.minecraft.network.play.server.SPacketChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// Mixin that changes SPacketChunkData.
@Mixin(SPacketChunkData.class)
public abstract class SPacketChunkDataMixin {
    // Changes a constant in readPacketData.
    @ModifyConstant(method = "readPacketData", constant = @Constant(intValue = 2097152))
    private int impetus$chunkDataLimit(int vanilla) {
        return NetworkLimits.chunkDataLimit();
    }
}
