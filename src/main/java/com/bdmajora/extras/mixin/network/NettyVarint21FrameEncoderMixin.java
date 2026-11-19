package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.NettyVarint21FrameEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Mixin that changes NettyVarint21FrameEncoder.
@Mixin(NettyVarint21FrameEncoder.class)
public abstract class NettyVarint21FrameEncoderMixin {
    @ModifyExpressionValue(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)V",
            at = @At(value = "CONSTANT", args = "intValue=" + NetworkLimits.VANILLA_FRAME_LENGTH_BYTES), require = 0)
    private int impetus$frameLengthBytes(int limit) {
        return limit == NetworkLimits.VANILLA_FRAME_LENGTH_BYTES ? NetworkLimits.frameLengthBytes() : limit;
    }
}
