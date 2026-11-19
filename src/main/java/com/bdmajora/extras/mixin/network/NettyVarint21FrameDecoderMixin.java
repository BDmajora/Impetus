package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.network.NetworkLimits;
import com.bdmajora.extras.network.QuietDecoderException;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Cancellable;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.DecoderException;
import io.netty.util.ByteProcessor;
import net.minecraft.network.NettyVarint21FrameDecoder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Mixin that changes NettyVarint21FrameDecoder.
@Mixin(NettyVarint21FrameDecoder.class)
public abstract class NettyVarint21FrameDecoderMixin {
    // Shared exception without a stack trace for a frame length that does not fit.
    @Unique
    private static final DecoderException IMPETUS_BAD_LENGTH = new QuietDecoderException("Bad packet length");

    // The fast path uses the same limit as the original path and yields when the check changes.
    @ModifyExpressionValue(method = "decode",
            at = @At(value = "CONSTANT", args = "intValue=" + NetworkLimits.VANILLA_FRAME_LENGTH_BYTES), require = 0)
    private int impetus$decodeFrameLimit(int limit, ChannelHandlerContext ctx, ByteBuf in, List<Object> out,
                                         @Cancellable CallbackInfo ci) {
        if (limit != NetworkLimits.VANILLA_FRAME_LENGTH_BYTES) {
            return limit;
        }
        int maxBytes = NetworkLimits.frameLengthBytes();
        if (Extras.options().network.fastVarInts) {
            this.impetus$fastDecode(ctx, in, out, maxBytes);
            ci.cancel();
        }
        return maxBytes;
    }

    @Unique
    private void impetus$fastDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out, int maxBytes) {
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

        int start = in.readerIndex();
        int readable = in.readableBytes();
        int length = 0;
        for (int i = 0; i < maxBytes; i++) {
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
