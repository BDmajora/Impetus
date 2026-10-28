package com.bdmajora.impetus.engine.impl.gl.buffer;

import java.nio.ByteBuffer;
import java.util.Objects;

// Mapped range of one buffer object and the memory buffer over it
public class GlBufferMapping {
    // Buffer object that owns the mapped range
    private final GlBuffer buffer;
    // Mapped memory exactly as the mapping call returned it
    private final ByteBuffer map;
    // Private view of the whole mapped memory from its start up to its capacity
    private final ByteBuffer target;

    // Disposal flag that dispose sets and write checks
    protected boolean disposed;

    // Stores the buffer and the mapped memory and rejects null for either
    public GlBufferMapping(GlBuffer buffer, ByteBuffer map) {
        this.buffer = Objects.requireNonNull(buffer, "Buffer must not be null");
        this.map = Objects.requireNonNull(map, "Memory buffer must not be null");
        this.target = map.duplicate().clear();
    }

    // Copies the remaining bytes of the data to the write offset of the mapped memory
    public void write(ByteBuffer data, int writeOffset) {
        // Rejects null data
        Objects.requireNonNull(data, "Data must not be null");

        // Rejects use after dispose
        if (this.disposed) {
            throw new IllegalStateException("Buffer mapping is already disposed");
        }

        int position = data.position();

        // Rejects a range past the capacity or a read only view and copies overlapping bytes as if through a temporary buffer
        this.target.put(writeOffset, data, position, data.limit() - position);
    }

    // Returns the buffer object that owns the mapped range
    public GlBuffer getBufferObject() {
        return this.buffer;
    }

    // Marks the mapping disposed and leaves the unmap to the device
    public void dispose() {
        this.disposed = true;
    }

    // Returns whether dispose has run
    public boolean isDisposed() {
        return this.disposed;
    }

    // Returns the mapped memory exactly as the mapping call returned it
    public ByteBuffer getMemoryBuffer() {
        return this.map;
    }
}
