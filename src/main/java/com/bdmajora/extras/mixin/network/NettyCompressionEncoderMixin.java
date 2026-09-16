package com.bdmajora.extras.mixin.network;

import com.bdmajora.extras.Extras;
import com.bdmajora.extras.network.CompressionScratch;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.NettyCompressionEncoder;
import net.minecraft.network.PacketBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.zip.Deflater;

// The sending side: vanilla copies every packet over the threshold into a new byte[] before deflating it; with pooled compression on, the buffer's own array or the handler's scratch is deflated in place, and the 8 KB staging array vanilla already keeps is retained for the output loop
@Mixin(NettyCompressionEncoder.class)
public abstract class NettyCompressionEncoderMixin {
    @Shadow
    @Final
    private byte[] buffer;

    @Shadow
    @Final
    private Deflater deflater;

    @Shadow
    private int threshold;

    private final CompressionScratch impetus$scratch = new CompressionScratch();

    @Overwrite
    protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
        int size = in.readableBytes();
        PacketBuffer packet = new PacketBuffer(out);
        if (size < this.threshold) {
            packet.writeVarInt(0);
            packet.writeBytes(in);
            return;
        }
        if (!Extras.options().network.pooledCompression) {
            byte[] raw = new byte[size];
            in.readBytes(raw);
            packet.writeVarInt(raw.length);
            this.deflater.setInput(raw, 0, size);
            this.deflater.finish();
            while (!this.deflater.finished()) {
                int n = this.deflater.deflate(this.buffer);
                packet.writeBytes(this.buffer, 0, n);
            }
            this.deflater.reset();
            return;
        }
        CompressionScratch scratch = this.impetus$scratch;
        scratch.load(in);
        try {
            packet.writeVarInt(size);
            this.deflater.setInput(scratch.array, scratch.offset, scratch.length);
            this.deflater.finish();
            while (!this.deflater.finished()) {
                int n = this.deflater.deflate(this.buffer);
                packet.writeBytes(this.buffer, 0, n);
            }
        } finally {
            this.deflater.reset();
            scratch.release();
        }
    }
}
