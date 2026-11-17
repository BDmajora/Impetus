package com.bdmajora.extras.network;

import io.netty.buffer.ByteBuf;

// Reusable buffers for packet decompression.
public final class CompressionScratch {
    // Starting size of the copy buffer.
    private static final int INITIAL = 8192;
    // Largest copy buffer kept between packets.
    private static final int RETAIN_LIMIT = 1 << 20;

    // Copy buffer for packets that have no backing array.
    private byte[] scratch = new byte[INITIAL];

    // Array holding the loaded bytes.
    public byte[] array;
    // Offset of the loaded bytes in the array.
    public int offset;
    // Number of loaded bytes.
    public int length;

    // Loads the value.
    public void load(ByteBuf in) {
        int readable = in.readableBytes();
        if (in.hasArray()) {
            this.array = in.array();
            this.offset = in.arrayOffset() + in.readerIndex();
            in.skipBytes(readable);
        } else {
            if (this.scratch.length < readable) {
                this.scratch = new byte[Math.max(readable, this.scratch.length * 2)];
            }
            in.readBytes(this.scratch, 0, readable);
            this.array = this.scratch;
            this.offset = 0;
        }
        this.length = readable;
    }

    // Releases the value.
    public void release() {
        this.array = null;
        if (this.scratch.length > RETAIN_LIMIT) {
            this.scratch = new byte[INITIAL];
        }
    }
}
