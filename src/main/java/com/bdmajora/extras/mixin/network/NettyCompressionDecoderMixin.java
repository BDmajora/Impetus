package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.network.CompressionScratch;
import com.bdmajora.extras.network.NetworkLimits;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.NettyCompressionDecoder;
import net.minecraft.network.PacketBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.zip.Inflater;

// Carries the "Badly compressed packet - size of X is larger than protocol maximum of 2097152" cap from Packet Fixer and, when pooled compression is on, Krypton's habit of not copying: the compressed bytes are read from the buffer's own array where it has one and the inflated packet lands in a pooled heap buffer instead of a fresh byte[] wrapped per packet
@Mixin(NettyCompressionDecoder.class)
public abstract class NettyCompressionDecoderMixin {
    @Shadow
    @Final
    private Inflater inflater;

    @Shadow
    private int threshold;

    private final CompressionScratch impetus$scratch = new CompressionScratch();

    // Vanilla's method with the limit read from the switch and the allocations replaced; the exception texts are vanilla's so log readers keep matching them
    @Overwrite
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() == 0) {
            return;
        }
        PacketBuffer buffer = new PacketBuffer(in);
        int size = buffer.readVarInt();
        if (size == 0) {
            out.add(buffer.readBytes(buffer.readableBytes()));
            return;
        }
        if (size < this.threshold) {
            throw new DecoderException("Badly compressed packet - size of " + size + " is below server threshold of " + this.threshold);
        }
        int limit = NetworkLimits.compressedPacketLimit();
        if (size > limit) {
            throw new DecoderException("Badly compressed packet - size of " + size + " is larger than protocol maximum of " + limit);
        }
        if (!Extras.options().network.pooledCompression) {
            byte[] compressed = new byte[buffer.readableBytes()];
            buffer.readBytes(compressed);
            this.inflater.setInput(compressed);
            byte[] inflated = new byte[size];
            this.inflater.inflate(inflated);
            out.add(io.netty.buffer.Unpooled.wrappedBuffer(inflated));
            this.inflater.reset();
            return;
        }
        CompressionScratch scratch = this.impetus$scratch;
        scratch.load(in);
        ByteBuf inflated = ctx.alloc().heapBuffer(size, size);
        try {
            this.inflater.setInput(scratch.array, scratch.offset, scratch.length);
            int written = 0;
            // inflate() may return before the whole packet is out when the input is consumed in pieces
            while (written < size && !this.inflater.finished()) {
                int n = this.inflater.inflate(inflated.array(), inflated.arrayOffset() + written, size - written);
                if (n == 0 && (this.inflater.needsInput() || this.inflater.needsDictionary())) {
                    break;
                }
                written += n;
            }
            inflated.writerIndex(written);
            out.add(inflated);
        } catch (Exception e) {
            inflated.release();
            throw e;
        } finally {
            this.inflater.reset();
            scratch.release();
        }
    }
}
