package com.bdmajora.impetus.engine.impl.render.mesh.gl;

import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL44;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Persistently and coherently mapped readback storage in client memory; GPU copies land here and become readable once the frame's fence signals, with no glMapBuffer stall
public class MappedDownloadBuffer {
    private static final int STORAGE_FLAGS = GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT | GL44.GL_CLIENT_STORAGE_BIT | GL30.GL_MAP_READ_BIT;
    private static final int MAP_FLAGS = GL44.GL_MAP_PERSISTENT_BIT | GL44.GL_MAP_COHERENT_BIT | GL30.GL_MAP_READ_BIT;

    private final int id;
    private final long size;
    private final long clientAddress;
    private boolean deleted;

    public MappedDownloadBuffer(long size) {
        this.size = size;
        this.id = LWJGL.glCreateBuffers();
        LWJGL.glNamedBufferStorage(this.id, size, STORAGE_FLAGS);
        this.clientAddress = LWJGL.nglMapNamedBufferRange(this.id, 0L, size, MAP_FLAGS);

        if (this.clientAddress == 0L) {
            throw new IllegalStateException("Failed to persistently map the terrain download buffer");
        }
    }

    // GL name
    public int getId() {
        return this.id;
    }

    // Bytes
    public long getSize() {
        return this.size;
    }

    // Base of the mapping
    public long getClientAddress() {
        return this.clientAddress;
    }

    // Unmaps, then deletes
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        LWJGL.glUnmapNamedBuffer(this.id);
        LWJGL.glDeleteBuffers(this.id);
    }
}
