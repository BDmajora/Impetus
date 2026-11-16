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

// Mixin that changes NettyCompressionEncoder.
@Mixin(NettyCompressionEncoder.class)
public abstract class NettyCompressionEncoderMixin {
    // Reference to the field buffer of the target.
    @Shadow
    @Final
    private byte[] buffer;

    // Reference to the field deflater of the target.
    @Shadow
    @Final
    private Deflater deflater;

    // Reference to the field threshold of the target.
    @Shadow
    private int threshold;

    // Reusable buffers of the encoder.
    private final CompressionScratch impetus$scratch = new CompressionScratch();

    // Replaces the whole method encode.
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
