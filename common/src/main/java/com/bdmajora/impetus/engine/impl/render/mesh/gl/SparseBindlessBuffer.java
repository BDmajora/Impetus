package com.bdmajora.impetus.engine.impl.render.mesh.gl;

import org.lwjgl.opengl.GL15;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.lwjgl.opengl.ARBSparseBuffer;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Device buffer with a huge address space where pages are committed on demand.
public class SparseBindlessBuffer implements DeviceBuffer {
    // Size of a committed page.
    public static final long PAGE_SIZE = 1L << 20;

    // GL name.
    private final int id;
    // Virtual size in bytes.
    private final long size;
    // GPU address.
    private final long deviceAddress;
    // Whether the buffer was deleted.
    private boolean deleted;

    // Rejects a deleted buffer and a range outside it.
    private void checkRange(long offset, long length) {
        if (this.deleted) {
            throw new IllegalStateException("Buffer was deleted");
        }
        if (offset < 0 || length < 0 || length > this.size - offset) {
            throw new IllegalArgumentException("Range outside the buffer " + offset + " " + length);
        }
    }

    // Number of allocations that touch each committed page.
    private final Int2IntOpenHashMap pageRefCounts = new Int2IntOpenHashMap();

    // Creates a buffer rounded up to whole pages and rejects a size that is not positive.
    public SparseBindlessBuffer(long requestedSize) {
        if (requestedSize <= 0) {
            throw new IllegalArgumentException("Buffer size must be positive");
        }
        this.size = alignUp(requestedSize, PAGE_SIZE);
        this.id = LWJGL.glCreateBuffers();
        LWJGL.glNamedBufferStorage(this.id, this.size, ARBSparseBuffer.GL_SPARSE_STORAGE_BIT_ARB);
        LWJGL.glMakeNamedBufferResidentNV(this.id, GL15.GL_READ_WRITE);
        this.deviceAddress = LWJGL.glGetNamedBufferGpuAddressNV(this.id);

        if (this.deviceAddress == 0L) {
            LWJGL.glMakeNamedBufferNonResidentNV(this.id);
            LWJGL.glDeleteBuffers(this.id);
            throw new IllegalStateException("Driver returned a null GPU address for a resident sparse buffer");
        }
    }

    // Returns a value rounded up to a multiple and rejects an alignment that is not positive.
    public static long alignUp(long value, long alignment) {
        if (alignment <= 0) {
            throw new IllegalArgumentException("Alignment must be positive");
        }
        long remainder = Math.floorMod(value, alignment);
        return remainder == 0 ? value : Math.addExact(value, alignment - remainder);
    }

    // Returns the GL name.
    @Override
    public int getId() {
        return this.id;
    }

    // Returns the virtual size.
    @Override
    public long getSize() {
        return this.size;
    }

    // Returns the GPU address.
    @Override
    public long getDeviceAddress() {
        return this.deviceAddress;
    }

    // Commits the pages of a byte range and counts a reference on each.
    public void ensureCommitted(long offset, long length) {
        this.checkRange(offset, length);
        if (length == 0) {
            return;
        }
        int firstPage = firstPage(offset);
        int lastPage = pageEnd(offset, length);

        commit(firstPage, lastPage - firstPage, true);

        for (int page = firstPage; page < lastPage; page++) {
            this.pageRefCounts.addTo(page, 1);
        }
    }

    // Drops a reference on the pages of a byte range and decommits pages without references.
    public void release(long offset, long length) {
        this.checkRange(offset, length);
        if (length == 0) {
            return;
        }
        int firstPage = firstPage(offset);
        int lastPage = pageEnd(offset, length);

        for (int page = firstPage; page < lastPage; page++) {
            int remaining = this.pageRefCounts.get(page) - 1;

            if (!this.pageRefCounts.containsKey(page)) {
                continue;
            }

            if (remaining > 0) {
                this.pageRefCounts.put(page, remaining);
            } else {
                this.pageRefCounts.remove(page);
                commit(page, 1, false);
            }
        }
    }

    // Returns the page of a byte offset.
    private static int firstPage(long offset) {
        return (int) (offset / PAGE_SIZE);
    }

    // Returns one past the last page of a byte range.
    private static int pageEnd(long offset, long length) {
        return (int) ((offset + length + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    // Returns the number of committed pages.
    public int getCommittedPages() {
        return this.pageRefCounts.size();
    }

    // Returns the bytes of the committed pages.
    public long getCommittedBytes() {
        return (long) this.pageRefCounts.size() * PAGE_SIZE;
    }

    // Makes the buffer non resident and deletes it once.
    @Override
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        this.pageRefCounts.clear();
        LWJGL.glMakeNamedBufferNonResidentNV(this.id);
        LWJGL.glDeleteBuffers(this.id);
    }

    // Commits or decommits a range of pages.
    private void commit(int firstPage, int pageCount, boolean commit) {
        if (pageCount <= 0) {
            return;
        }
        LWJGL.glBindBuffer(GL15.GL_ARRAY_BUFFER, this.id);
        LWJGL.glBufferPageCommitmentARB(GL15.GL_ARRAY_BUFFER, firstPage * PAGE_SIZE, pageCount * PAGE_SIZE, commit);
        LWJGL.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }
}
