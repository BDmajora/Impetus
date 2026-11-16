package com.bdmajora.extras.network;

import io.netty.buffer.ByteBuf;

// Reusable buffers for packet decompression.
public final class CompressionScratch {
    // Constant initial.
    private static final int INITIAL = 8192;
    // Constant retain limit.
    private static final int RETAIN_LIMIT = 1 << 20;

    // Choice of the scratch option.
    private byte[] scratch = new byte[INITIAL];

    // Choice of the array option.
    public byte[] array;
    // Value of the offset option.
    public int offset;
    // Value of the length option.
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
