package com.bdmajora.impetus.engine.impl.render.mesh.gl;

import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL44;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Persistently mapped buffer that receives copies from the GPU.
public class MappedDownloadBuffer {
    // Storage flags of the buffer.
    private static final int STORAGE_FLAGS = GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT | GL44.GL_CLIENT_STORAGE_BIT | GL30.GL_MAP_READ_BIT;
    // Flags of the mapping.
    private static final int MAP_FLAGS = GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT | GL30.GL_MAP_READ_BIT;

    // GL name.
    private final int id;
    // Size in bytes.
    private final long size;
    // Address of the mapping.
    private final long clientAddress;
    // Whether the buffer was deleted.
    private boolean deleted;

    // Creates and maps the buffer and rejects a size that is not positive.
    public MappedDownloadBuffer(long size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive");
        }
        this.size = size;
        this.id = LWJGL.glCreateBuffers();
        LWJGL.glNamedBufferStorage(this.id, size, STORAGE_FLAGS);
        this.clientAddress = LWJGL.nglMapNamedBufferRange(this.id, 0L, size, MAP_FLAGS);

        if (this.clientAddress == 0L) {
            LWJGL.glDeleteBuffers(this.id);
            throw new IllegalStateException("Failed to persistently map the terrain download buffer");
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
