package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.network.NetworkLimits;
import net.minecraft.network.NettyVarint21FrameEncoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// The sending side of the same 21-bit cap; both occurrences of the constant (the check and its message) move together
@Mixin(NettyVarint21FrameEncoder.class)
public abstract class NettyVarint21FrameEncoderMixin {
    // The typed encode, not the (ctx, Object, ByteBuf) bridge javac generates beside it
    @ModifyConstant(method = "encode(Lio/netty/channel/ChannelHandlerContext;Lio/netty/buffer/ByteBuf;Lio/netty/buffer/ByteBuf;)V", constant = @Constant(intValue = 3))
    private int impetus$frameLengthBytes(int vanilla) {
        return NetworkLimits.frameLengthBytes();
    }
}
