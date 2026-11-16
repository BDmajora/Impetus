package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import net.minecraft.network.NettyVarint21FrameEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// Mixin that changes NettyVarint21FrameEncoder.
@Mixin(NettyVarint21FrameEncoder.class)
public abstract class NettyVarint21FrameEncoderMixin {
    // Changes a constant in encode.
    @ModifyConstant(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)V", constant = @Constant(intValue = 3))
    private int impetus$frameLengthBytes(int vanilla) {
        return NetworkLimits.frameLengthBytes();
    }
}
