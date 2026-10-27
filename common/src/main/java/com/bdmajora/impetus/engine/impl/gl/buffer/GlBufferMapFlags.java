package com.bdmajora.impetus.engine.impl.gl.buffer;

import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL44;

import com.bdmajora.impetus.engine.impl.gl.util.EnumBit;

// Access flags that glMapBufferRange accepts as single GL bits
public enum GlBufferMapFlags implements EnumBit {
    // Flag that maps the range for reading
    READ(GL30.GL_MAP_READ_BIT),
    // Flag that maps the range for writing
    WRITE(GL30.GL_MAP_WRITE_BIT),
    // Flag that keeps the mapping open while the GPU uses the buffer
    PERSISTENT(GL44.GL_MAP_PERSISTENT_BIT),
    // Flag that lets GL discard the previous contents of the whole buffer
    INVALIDATE_BUFFER(GL30.GL_MAP_INVALIDATE_BUFFER_BIT),
    // Flag that lets GL discard the previous contents of the mapped range
    INVALIDATE_RANGE(GL30.GL_MAP_INVALIDATE_RANGE_BIT),
    // Flag that requires an explicit flush of each written range
    EXPLICIT_FLUSH(GL30.GL_MAP_FLUSH_EXPLICIT_BIT),
    // Flag that makes mapped writes visible to the GPU without a flush
    COHERENT(GL44.GL_MAP_COHERENT_BIT),
    // Flag that skips the wait for pending GPU work on the buffer
    UNSYNCHRONIZED(GL30.GL_MAP_UNSYNCHRONIZED_BIT);

    // GL bit of the flag
    private final int bit;

    // Stores the GL bit of the flag
    GlBufferMapFlags(int bit) {
        this.bit = bit;
    }

    // Returns the GL bit of the flag
    @Override
    public int getBits() {
        return this.bit;
    }
}
