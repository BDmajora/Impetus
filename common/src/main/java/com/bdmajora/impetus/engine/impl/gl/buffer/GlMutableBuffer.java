package com.bdmajora.impetus.engine.impl.gl.buffer;

// Buffer object whose storage the device reallocates in place and whose storage size it records
public class GlMutableBuffer extends GlBuffer {
    // Size of the current storage in bytes
    private long size;

    // Generates a buffer name with no storage
    public GlMutableBuffer() {
        super();
    }

    // Stores the size of new storage and clears and disposes the open mapping
    public void setSize(long size) {
        // Rejects a deleted buffer or a negative size
        if (size < 0L | !this.isHandleValid()) {
            throw this.sizeError();
        }

        this.size = size;

        GlBufferMapping mapping = this.getActiveMapping();

        // Clears and disposes the mapping that the new storage unmaps
        if (mapping != null) {
            this.setActiveMapping(null);
            mapping.dispose();
        }
    }

    // Returns the size of the current storage in bytes
    public long getSize() {
        return this.size;
    }

    // Returns the refusal for a size that the setter rejects
    private RuntimeException sizeError() {
        // Throws on a deleted buffer
        this.checkHandle();

        return new IllegalArgumentException("Size must not be negative");
    }

    // Deletes the buffer name and disposes the open mapping and clears the size
    @Override
    protected void destroyInternal() {
        super.destroyInternal();

        this.size = 0L;
    }
}
