package com.bdmajora.impetus.engine.impl.render.mesh.util;

import com.bdmajora.impetus.engine.impl.render.mesh.MeshShaderSupport;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.BindlessBuffer;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.DeviceBuffer;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.SparseBindlessBuffer;

// Buffer that holds the quads of every section.
public class QuadArena {
    // Address space of a sparse buffer in bytes.
    private static final long SPARSE_ADDRESS_SPACE = 80L * 1024L * 1024L * 1024L;

    // Allocator of the quad ranges.
    private final SegmentedAllocator allocator = new SegmentedAllocator();
    // Backing buffer.
    private final DeviceBuffer buffer;
    // Bytes of one vertex.
    private final int bytesPerVertex;
    // Size of a dense buffer or zero.
    private final long denseCapacity;

    // Number of quads in use.
    private long liveQuads;

    // Creates an arena and rejects a negative budget or a vertex size that is not positive.
    public QuadArena(long memoryBudget, int bytesPerVertex) {
        if (memoryBudget < 0 || bytesPerVertex <= 0) {
            throw new IllegalArgumentException("Invalid budget " + memoryBudget + " or vertex size " + bytesPerVertex);
        }
        this.bytesPerVertex = bytesPerVertex;

        if (MeshShaderSupport.supportsSparseGeometry()) {
            this.buffer = new SparseBindlessBuffer(SPARSE_ADDRESS_SPACE);
            this.denseCapacity = 0L;
        } else {
            this.buffer = new BindlessBuffer(memoryBudget);
            this.denseCapacity = memoryBudget;
            this.allocator.setLimit(memoryBudget / (4L * bytesPerVertex));
        }

        this.alloc(1);
    }

    // Returns the bytes of one quad.
    public int bytesPerQuad() {
        return Math.multiplyExact(4, this.bytesPerVertex);
    }

    // Returns the backing buffer.
    public DeviceBuffer getBuffer() {
        return this.buffer;
    }

    // Returns the quad index of a new range or the out of space marker.
    public int alloc(int quadCount) {
        long address = this.allocator.alloc(quadCount);

        if (address == SegmentedAllocator.OUT_OF_SPACE) {
            return (int) SegmentedAllocator.OUT_OF_SPACE;
        }

        this.liveQuads += quadCount;

        if (this.buffer instanceof SparseBindlessBuffer sparse) {
            sparse.ensureCommitted(byteOffset((int) address), byteLength(quadCount));
        }

        return (int) address;
    }

    // Releases a quad range.
    public void free(int quadAddress) {
        int quadCount = this.allocator.free(quadAddress);
        this.liveQuads -= quadCount;

        if (this.buffer instanceof SparseBindlessBuffer sparse) {
            sparse.release(byteOffset(quadAddress), byteLength(quadCount));
        }
    }

    // Returns whether an allocation has exactly a quad count.
    public boolean canReuse(int quadAddress, int quadCount) {
        return this.allocator.getSize(quadAddress) == quadCount;
    }

    // Reserves staging space for an allocation and returns the write address.
    public long beginUpload(UploadStream stream, int quadAddress) {
        long quadCount = this.allocator.getSize(quadAddress);
        return stream.upload(this.buffer, byteOffset(quadAddress), byteLength((int) quadCount));
    }

    // Returns the bytes that the arena occupies in video memory.
    public long getResidentBytes() {
        if (this.buffer instanceof SparseBindlessBuffer sparse) {
            return sparse.getCommittedBytes();
        }
        return this.denseCapacity;
    }

    // Returns the bytes of the quads in use.
    public long getUsedBytes() {
        return byteLength((int) this.liveQuads);
    }

    // Deletes the backing buffer.
    public void delete() {
        this.buffer.delete();
    }

    // Returns the byte offset of a quad index.
    private long byteOffset(int quadAddress) {
        return Integer.toUnsignedLong(quadAddress) * 4L * this.bytesPerVertex;
    }

    // Returns the bytes of a number of quads.
    private long byteLength(int quadCount) {
        return (long) quadCount * 4L * this.bytesPerVertex;
    }
}
