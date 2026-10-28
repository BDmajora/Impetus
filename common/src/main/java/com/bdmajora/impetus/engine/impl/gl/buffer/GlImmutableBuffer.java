package com.bdmajora.impetus.engine.impl.gl.buffer;

import java.util.Objects;

import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;

// Buffer object whose storage flags stay fixed for its lifetime
public class GlImmutableBuffer extends GlBuffer {
    // Storage flags that the buffer storage is allocated with
    private final EnumBitField<GlBufferStorageFlags> flags;

    // Rejects null flags before any buffer name is generated and stores the flags
    public GlImmutableBuffer(EnumBitField<GlBufferStorageFlags> flags) {
        Objects.requireNonNull(flags, "Flags must not be null");

        super();

        this.flags = flags;
    }

    // Returns the storage flags that the buffer storage is allocated with
    public EnumBitField<GlBufferStorageFlags> getFlags() {
        return this.flags;
    }
}
