package com.bdmajora.impetus.engine.impl.render.mesh.gl;

import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL44;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Persistently mapped buffer that the CPU writes and the GPU copies from.
public class MappedUploadBuffer {
    // Storage flags of the buffer.
    private static final int STORAGE_FLAGS = GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_CLIENT_STORAGE_BIT | GL30.GL_MAP_WRITE_BIT;
    // Flags of the mapping.
    private static final int MAP_FLAGS = GL44.GL_MAP_PERSISTENT_BIT | GL30.GL_MAP_UNSYNCHRONIZED_BIT
            | GL30.GL_MAP_FLUSH_EXPLICIT_BIT | GL30.GL_MAP_WRITE_BIT;

    // GL name.
    private final int id;
    // Size in bytes.
    private final long size;
    // Address of the mapping.
    private final long clientAddress;
    // Whether the buffer was deleted.
    private boolean deleted;

    // Creates and maps the buffer and rejects a size that is not positive.
    public MappedUploadBuffer(long size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive");
        }
        this.size = size;
        this.id = LWJGL.glCreateBuffers();
        LWJGL.glNamedBufferStorage(this.id, size, STORAGE_FLAGS);
        this.clientAddress = LWJGL.nglMapNamedBufferRange(this.id, 0L, size, MAP_FLAGS);

        if (this.clientAddress == 0L) {
            LWJGL.glDeleteBuffers(this.id);
            throw new IllegalStateException("Failed to persistently map the terrain upload buffer");
        }
    }

    // Returns the GL name.
    public int getId() {
        return this.id;
    }

    // Returns the size in bytes.
    public long getSize() {
        return this.size;
    }

    // Returns the address of the mapping.
    public long getClientAddress() {
        return this.clientAddress;
    }

    // Makes a written range visible to the GPU and rejects a range outside the buffer.
    public void flush(long offset, long length) {
        if (this.deleted) {
            throw new IllegalStateException("Buffer was deleted");
        }
        if (offset < 0 || length < 0 || length > this.size - offset) {
            throw new IllegalArgumentException("Range outside the buffer " + offset + " " + length);
        }
        LWJGL.glFlushMappedNamedBufferRange(this.id, offset, length);
    }

    // Unmaps and deletes the buffer once.
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        LWJGL.glUnmapNamedBuffer(this.id);
        LWJGL.glDeleteBuffers(this.id);
    }
}
