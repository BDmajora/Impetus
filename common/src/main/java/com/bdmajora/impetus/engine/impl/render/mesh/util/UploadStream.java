package com.bdmajora.impetus.engine.impl.render.mesh.util;

import com.bdmajora.impetus.engine.impl.gl.sync.GlFence;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.DeviceBuffer;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.MappedUploadBuffer;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL42;
import org.lwjgl.opengl.GL44;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// Copies data to device buffers through a mapped staging ring.
public class UploadStream {
    // Allocator of the staging ring.
    private final SegmentedAllocator allocator = new SegmentedAllocator();
    // Staging buffer.
    private final MappedUploadBuffer staging;

    // Copies queued for the next commit.
    private final Deque<PendingCopy> pending = new ArrayDeque<>();
    // Staging allocations that were written and need a flush.
    private final LongArrayList toFlush = new LongArrayList();
    // Staging allocations flushed in this frame.
    private final LongArrayList inFlight = new LongArrayList();
    // Frames that wait for their fence.
    private final Deque<Frame> frames = new ArrayDeque<>();

    // Allocation that the next upload extends or minus one.
    private long currentAllocation = -1L;
    // Bytes written into the current allocation.
    private long currentOffset;
    // Deleted state of the stream.
    private boolean deleted;

    // Creates a stream with a staging ring of a size.
    public UploadStream(long size) {
        this.allocator.setLimit(size);
        this.staging = new MappedUploadBuffer(size);
    }

    // Reserves staging space and returns the address to write and rejects a deleted stream or a null target or a range outside it.
    public long upload(DeviceBuffer target, long targetOffset, long size) {
        this.checkLive();
        Objects.requireNonNull(target, "target");
        if (size <= 0 || size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Bad upload size: " + size);
        }
        if (targetOffset < 0 || size > target.getSize() - targetOffset) {
            throw new IllegalArgumentException("Upload runs past the end of the target buffer");
        }

        long address;

        if (this.currentAllocation != -1L && this.allocator.expand(this.currentAllocation, (int) size)) {
            address = this.currentAllocation + this.currentOffset;
            this.currentOffset += size;
        } else {
            this.currentAllocation = this.allocator.alloc((int) size);

            if (this.currentAllocation == SegmentedAllocator.OUT_OF_SPACE) {
                this.currentAllocation = reclaimSpace((int) size);
            }

            this.toFlush.add(this.currentAllocation);
            this.currentOffset = size;
            address = this.currentAllocation;
        }

        this.pending.add(new PendingCopy(target, address, targetOffset, size));

        return this.staging.getClientAddress() + address;
    }

    // Flushes the staging writes and issues the queued copies and rejects a deleted stream.
    public void commit() {
        this.checkLive();
        if (!this.toFlush.isEmpty()) {
            for (int i = 0; i < this.toFlush.size(); i++) {
                long allocation = this.toFlush.getLong(i);
                this.staging.flush(allocation, this.allocator.getSize(allocation));
                this.inFlight.add(allocation);
            }
            this.toFlush.clear();
        }

        if (!this.pending.isEmpty()) {
            LWJGL.glMemoryBarrier(GL44.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT);

            for (PendingCopy copy : this.pending) {
                LWJGL.glCopyNamedBufferSubData(this.staging.getId(), copy.target.getId(),
                        copy.stagingOffset, copy.targetOffset, copy.size);
            }
            this.pending.clear();

            LWJGL.glMemoryBarrier(GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
        }

        this.currentAllocation = -1L;
        this.currentOffset = 0L;
    }

    // Commits the queued copies and fences the frame and reclaims finished frames and rejects a deleted stream.
    public void endFrame() {
        this.commit();

        if (!this.inFlight.isEmpty()) {
            this.frames.add(new Frame(createFence(), new LongArrayList(this.inFlight)));
            this.inFlight.clear();
        }

        releaseCompletedFrames();
    }

    // Deletes the staging ring once.
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;

        try {
            for (Frame frame : this.frames) {
                frame.fence.delete();
            }
        } finally {
            this.frames.clear();
            this.pending.clear();
            this.staging.delete();
        }
    }

    // Frees the staging space of the frames the GPU finished.
    private void releaseCompletedFrames() {
        while (!this.frames.isEmpty()) {
            if (!this.frames.peek().fence.isCompleted()) {
                break;
            }

            Frame frame = this.frames.poll();

            for (int i = 0; i < frame.allocations.size(); i++) {
                this.allocator.free(frame.allocations.getLong(i));
            }

            frame.fence.delete();
        }
    }

    // Waits for earlier frames until a request fits and rejects a request that never fits.
    private long reclaimSpace(int size) {
        this.commit();

        for (int attempt = 0; attempt < 10; attempt++) {
            LWJGL.glFinish();
            this.endFrame();

            long address = this.allocator.alloc(size);

            if (address != SegmentedAllocator.OUT_OF_SPACE) {
                return address;
            }
        }

        throw new IllegalStateException("Terrain upload buffer could not fit a " + size + " byte upload after flushing");
    }

    // Rejects a deleted stream.
    private void checkLive() {
        if (this.deleted) {
            throw new IllegalStateException("Upload stream was deleted");
        }
    }

    // Creates a fence for the commands so far.
    private static GlFence createFence() {
        return new GlFence(LWJGL.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0));
    }

    // Copy from staging to a target.
    private record PendingCopy(DeviceBuffer target, long stagingOffset, long targetOffset, long size) {}

    // Staging allocations of one frame and its fence.
    private record Frame(GlFence fence, LongArrayList allocations) {}
}
