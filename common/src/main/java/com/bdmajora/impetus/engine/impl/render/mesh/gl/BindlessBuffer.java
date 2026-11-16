package com.bdmajora.impetus.engine.impl.render.mesh.gl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Device local buffer that shaders reach by a GPU address.
public class BindlessBuffer implements DeviceBuffer {
    // GL name.
    private final int id;
    // Size in bytes.
    private final long size;
    // GPU address.
    private final long deviceAddress;
    // Whether the buffer was deleted.
    private boolean deleted;

    // Rejects a deleted buffer.
    private void checkLive() {
        if (this.deleted) {
            throw new IllegalStateException("Buffer was deleted");
        }
    }

    // Creates a resident buffer and rejects a size that is not positive.
    public BindlessBuffer(long size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive");
        }
        this.size = size;
        this.id = LWJGL.glCreateBuffers();
        LWJGL.glNamedBufferStorage(this.id, size, 0);
        LWJGL.glMakeNamedBufferResidentNV(this.id, GL15.GL_READ_WRITE);
        this.deviceAddress = LWJGL.glGetNamedBufferGpuAddressNV(this.id);

        if (this.deviceAddress == 0L) {
            LWJGL.glMakeNamedBufferNonResidentNV(this.id);
            LWJGL.glDeleteBuffers(this.id);
            throw new IllegalStateException("Driver returned a null GPU address for a resident buffer");
        }
    }

    // Returns the GL name.
    @Override
    public int getId() {
        return this.id;
    }

    // Returns the size in bytes.
    @Override
    public long getSize() {
        return this.size;
    }

    // Returns the GPU address.
    @Override
    public long getDeviceAddress() {
        return this.deviceAddress;
    }

    // Zeroes the whole buffer on the GPU.
    public void clear() {
        this.checkLive();
        LWJGL.glClearNamedBufferDataZero(this.id, GL30.GL_R8UI, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_BYTE);
    }

    // Zeroes a byte range and rejects a range outside the buffer.
    public void clearRange(long offset, long length) {
        this.checkLive();
        if (offset < 0 || length < 0 || length > this.size - offset) {
            throw new IllegalArgumentException("Range outside the buffer " + offset + " " + length);
        }
        if (length == 0) {
            return;
        }
        LWJGL.glClearNamedBufferSubDataZero(this.id, GL30.GL_R8UI, offset, length, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_BYTE);
    }

    // Makes the buffer non resident and deletes it once.
    @Override
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        LWJGL.glMakeNamedBufferNonResidentNV(this.id);
        LWJGL.glDeleteBuffers(this.id);
    }
}
