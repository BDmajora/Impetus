package com.bdmajora.impetus.engine.impl.gl.buffer;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

import com.bdmajora.impetus.engine.impl.gl.GlObject;

// Owner of one buffer object name from the LWJGL service and of the mapping open on it
public abstract class GlBuffer extends GlObject {
    // Reserved name that names no buffer
    private static final int NULL_BUFFER_ID = 0;

    // Mapping open on the buffer or null when the buffer is unmapped
    private GlBufferMapping activeMapping;

    // Generates a buffer name and rejects a zero name from the driver
    protected GlBuffer() {
        int name = LWJGL.glGenBuffers();

        if (name == NULL_BUFFER_ID) {
            throw new IllegalStateException("Failed to generate a buffer");
        }

        this.setHandle(name);
    }

    // Returns the open mapping or null when the buffer is unmapped
    public GlBufferMapping getActiveMapping() {
        return this.activeMapping;
    }

    // Stores the open mapping or clears it when given null
    public void setActiveMapping(GlBufferMapping mapping) {
        GlBufferMapping active = this.activeMapping;

        // Rejects a deleted buffer or a foreign or disposed mapping or a second mapping
        if (mapping != null && mapping != active
                && (active != null | mapping.getBufferObject() != this | mapping.isDisposed() | !this.isHandleValid())) {
            throw this.mappingError(mapping);
        }

        this.activeMapping = mapping;
    }

    // Returns the refusal for a mapping that the setter rejects
    private RuntimeException mappingError(GlBufferMapping mapping) {
        // Throws on a deleted buffer
        this.checkHandle();

        if (mapping.getBufferObject() != this) {
            return new IllegalArgumentException("Mapping belongs to another buffer");
        }

        if (mapping.isDisposed()) {
            return new IllegalStateException("Buffer mapping is already disposed");
        }

        return new IllegalStateException("Buffer is already mapped");
    }

    // Deletes the buffer name and clears and disposes the mapping that the deletion unmaps
    @Override
    protected void destroyInternal() {
        LWJGL.glDeleteBuffers(this.handle());

        GlBufferMapping mapping = this.activeMapping;

        if (mapping != null) {
            this.activeMapping = null;
            mapping.dispose();
        }
    }
}
