package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import net.minecraft.network.NettyCompressionDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// "Badly compressed packet - size of X is larger than protocol maximum of 2097152": the inflated size cap on received packets
@Mixin(NettyCompressionDecoder.class)
public abstract class NettyCompressionDecoderMixin {
    @ModifyConstant(method = "decode", constant = @Constant(intValue = 2097152))
    private int impetus$compressedLimit(int vanilla) {
        return NetworkLimits.compressedPacketLimit();
    }
}
