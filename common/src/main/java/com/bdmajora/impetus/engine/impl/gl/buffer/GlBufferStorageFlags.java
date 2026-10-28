package com.bdmajora.impetus.engine.impl.gl.buffer;

import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL44;

import com.bdmajora.impetus.engine.impl.gl.util.EnumBit;

// Storage flags that glBufferStorage accepts as single GL bits
public enum GlBufferStorageFlags implements EnumBit {
    // Flag that lets a mapping stay open while the GPU uses the buffer
    PERSISTENT(GL44.GL_MAP_PERSISTENT_BIT),
    // Flag that allows the buffer to be mapped for reading
    MAP_READ(GL30.GL_MAP_READ_BIT),
    // Flag that allows the buffer to be mapped for writing
    MAP_WRITE(GL30.GL_MAP_WRITE_BIT),
    // Flag that asks GL to keep the storage in client memory
    CLIENT_STORAGE(GL44.GL_CLIENT_STORAGE_BIT),
    // Flag that allows coherent mappings of the buffer
    COHERENT(GL44.GL_MAP_COHERENT_BIT);

    // GL bit of the flag
    private final int bits;

    // Stores the GL bit of the flag
    GlBufferStorageFlags(int bits) {
        this.bits = bits;
    }

    // Returns the GL bit of the flag
    @Override
    public int getBits() {
        return this.bits;
    }
}
