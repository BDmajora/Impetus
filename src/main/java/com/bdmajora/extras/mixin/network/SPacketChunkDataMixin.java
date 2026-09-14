package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import net.minecraft.network.play.server.SPacketChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// "Chunk Packet trying to allocate too much memory on read": a chunk full of modded block states with wide palettes can exceed 2 MB
@Mixin(SPacketChunkData.class)
public abstract class SPacketChunkDataMixin {
    @ModifyConstant(method = "readPacketData", constant = @Constant(intValue = 2097152))
    private int impetus$chunkDataLimit(int vanilla) {
        return NetworkLimits.chunkDataLimit();
    }
}
