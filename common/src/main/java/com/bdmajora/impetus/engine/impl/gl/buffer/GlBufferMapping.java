package com.bdmajora.impetus.engine.impl.gl.buffer;


import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

public class GlBufferMapping {
    private final GlBuffer buffer;
    private final ByteBuffer map;

    protected boolean disposed;

    public GlBufferMapping(GlBuffer buffer, ByteBuffer map) {
        this.buffer = buffer;
        this.map = map;
    }

    // Copies into the mapped range
    public void write(ByteBuffer data, int writeOffset) {
        MemoryUtil.memCopy(MemoryUtil.memAddress(data), MemoryUtil.memAddress(this.map, writeOffset), data.remaining());
    }

    // The buffer this maps
    public GlBuffer getBufferObject() {
        return this.buffer;
    }

    // Marks unmapped; the device does the actual unmap
    public void dispose() {
        this.disposed = true;
    }

    // Whether dispose has run
    public boolean isDisposed() {
        return this.disposed;
    }

    // The mapped memory
    public ByteBuffer getMemoryBuffer() {
        return this.map;
    }
}
