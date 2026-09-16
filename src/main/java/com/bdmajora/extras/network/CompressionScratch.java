package com.bdmajora.extras.network;

import io.netty.buffer.ByteBuf;

// Per-handler input scratch for the compression codecs: vanilla allocates a fresh byte[] of the packet's size on every compressed packet in both directions just to hand Deflater/Inflater a heap array. A buffer that already sits on a heap array is used in place, anything else is copied into one growable array kept for the handler's lifetime (each connection owns its codec, so no sharing)
public final class CompressionScratch {
    private static final int INITIAL = 8192;
    // A single huge packet should not pin its buffer forever
    private static final int RETAIN_LIMIT = 1 << 20;

    private byte[] scratch = new byte[INITIAL];

    // The array holding the buffer's readable bytes, with the offset the caller must start reading at
    public byte[] array;
    public int offset;
    public int length;

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

    // Drops references so a released ByteBuf's array is not held, and trims an oversized scratch
    public void release() {
        this.array = null;
        if (this.scratch.length > RETAIN_LIMIT) {
            this.scratch = new byte[INITIAL];
        }
    }
}
