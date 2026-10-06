package com.bdmajora.impetus.engine.impl.render.mesh.gl;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Immutable resident device-local storage reached by raw 64-bit pointer; never CPU-mapped, writes arrive via UploadStream staging so the driver keeps it in its fastest memory
public class BindlessBuffer implements DeviceBuffer {
    private final int id;
    private final long size;
    private final long deviceAddress;
    private boolean deleted;

    public BindlessBuffer(long size) {
        this.size = size;
        this.id = LWJGL.glCreateBuffers();
        // No storage flags at all: no client access of any kind, the strongest hint the driver gets
        LWJGL.glNamedBufferStorage(this.id, size, 0);
        LWJGL.glMakeNamedBufferResidentNV(this.id, GL15.GL_READ_WRITE);
        this.deviceAddress = LWJGL.glGetNamedBufferGpuAddressNV(this.id);

        if (this.deviceAddress == 0L) {
            throw new IllegalStateException("Driver returned a null GPU address for a resident buffer");
        }
    }

    // GL name
    @Override
    public int getId() {
        return this.id;
    }

    // Bytes
    @Override
    public long getSize() {
        return this.size;
    }

    // GPU virtual address, for the NV bindless path
    @Override
    public long getDeviceAddress() {
        return this.deviceAddress;
    }

    // Zeroes the whole buffer on the GPU; no staging copy and no CPU-side array
    public void clear() {
        LWJGL.glClearNamedBufferDataZero(this.id, GL30.GL_R8UI, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_BYTE);
    }

    // Zeroes a byte range; wipes a region's visibility bytes the frame it leaves the frustum so stale flags cannot resurrect it
    public void clearRange(long offset, long length) {
        LWJGL.glClearNamedBufferSubDataZero(this.id, GL30.GL_R8UI, offset, length, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_BYTE);
    }

    // Makes non-resident, then deletes
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
