package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.network.NetworkLimits;
import com.bdmajora.extras.network.QuietDecoderException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ByteProcessor;
import net.minecraft.network.NettyVarint21FrameDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// The frame splitter, on two switches: large packets widen vanilla's three-byte length scratch to five, and fast varints replace the whole decode with Krypton's, which reads the length in place instead of copying it through a fresh array, a wrapped buffer and a PacketBuffer per frame, hands the frame on as a retained slice rather than a copy, and skips runs of zero bytes at once rather than one empty frame each (Velocity's "nullping" fix)
@Mixin(NettyVarint21FrameDecoder.class)
public abstract class NettyVarint21FrameDecoderMixin {
    // No stack trace: a malformed length is the peer's fault and the trace would only ever point here
    @Unique
    private static final DecoderException IMPETUS_BAD_LENGTH = new QuietDecoderException("Bad packet length");

    @ModifyConstant(method = "decode", constant = @Constant(intValue = 3))
    private int impetus$frameLengthBytes(int vanilla) {
        return NetworkLimits.frameLengthBytes();
    }

    @Inject(method = "decode", at = @At("HEAD"), cancellable = true)
    private void impetus$fastDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out, CallbackInfo ci) {
        if (!Extras.options().network.fastVarInts) {
            return;
        }
        ci.cancel();

        if (!ctx.channel().isActive()) {
            in.clear();
            return;
        }

        int packetStart = in.forEachByte(ByteProcessor.FIND_NON_NUL);
        if (packetStart == -1) {
            in.clear();
            return;
        }
        in.readerIndex(packetStart);

        int maxBytes = NetworkLimits.frameLengthBytes();
        int start = in.readerIndex();
        int readable = in.readableBytes();
        int length = 0;
        for (int i = 0; i < maxBytes; i++) {
            // Not enough of the prefix has arrived yet; nothing was consumed, so the next read starts over here
            if (i >= readable) {
                return;
            }
            byte b = in.getByte(start + i);
            length |= (b & 0x7F) << (7 * i);
            if (b >= 0) {
                if (length < 0) {
                    throw IMPETUS_BAD_LENGTH;
                }
                int prefix = i + 1;
                if (readable - prefix < length) {
                    return;
                }
                in.skipBytes(prefix);
                if (length > 0) {
                    out.add(in.readRetainedSlice(length));
                }
                return;
            }
        }

        throw new CorruptedFrameException("length wider than " + (maxBytes * 7) + "-bit");
    }
}
