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

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

// The only path to a device buffer: upload hands back a fenced staging address (never overwritten in flight), commit flushes and batches the copies
public class UploadStream {
    private final SegmentedAllocator allocator = new SegmentedAllocator();
    private final MappedUploadBuffer staging;

    // Copies queued for the next commit
    private final Deque<PendingCopy> pending = new ArrayDeque<>();
    // Staging allocations written since the last commit and still needing a flush
    private final LongArrayList toFlush = new LongArrayList();
    // Staging allocations flushed this frame, waiting on the frame's fence before they can be reused
    private final LongArrayList inFlight = new LongArrayList();
    private final Deque<Frame> frames = new ArrayDeque<>();

    // The allocation the next upload tries to extend and how far into it we have written; -1 forces a fresh allocation
    private long currentAllocation = -1L;
    private long currentOffset;

    public UploadStream(long size) {
        this.allocator.setLimit(size);
        this.staging = new MappedUploadBuffer(size);
    }

    // Reserves size bytes of staging space and returns the address to write to; valid only until the next commit()
    public long upload(DeviceBuffer target, long targetOffset, long size) {
        if (size <= 0 || size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Bad upload size: " + size);
        }
        if (targetOffset < 0 || targetOffset + size > target.getSize()) {
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

    // Flushes staging writes and issues every queued copy; once per frame from the pipeline, and again from reclaimSpace when the ring fills mid-frame
    public void commit() {
        if (!this.toFlush.isEmpty()) {
            for (int i = 0; i < this.toFlush.size(); i++) {
                long allocation = this.toFlush.getLong(i);
                this.staging.flush(allocation, this.allocator.getSize(allocation));
                this.inFlight.add(allocation);
            }
            this.toFlush.clear();
        }

        if (!this.pending.isEmpty()) {
            // Make the persistent mapping's writes visible to the copies that follow
            LWJGL.glMemoryBarrier(GL44.GL_CLIENT_MAPPED_BUFFER_BARRIER_BIT);

            for (PendingCopy copy : this.pending) {
                LWJGL.glCopyNamedBufferSubData(this.staging.getId(), copy.target.getId(),
                        copy.stagingOffset, copy.targetOffset, copy.size);
            }
            this.pending.clear();

            // And the copies' writes visible to the shaders that read them through resident pointers
            LWJGL.glMemoryBarrier(GL42.GL_BUFFER_UPDATE_BARRIER_BIT);
        }

        this.currentAllocation = -1L;
        this.currentOffset = 0L;
    }

    // Closes the frame: commits what is left, fences it, and reclaims staging space of every earlier frame the GPU finished
    public void endFrame() {
        this.commit();

        if (!this.inFlight.isEmpty()) {
            this.frames.add(new Frame(createFence(), new LongArrayList(this.inFlight)));
            this.inFlight.clear();
        }

        releaseCompletedFrames();
    }

    // Frees the staging ring
    public void delete() {
        for (Frame frame : this.frames) {
            frame.fence.delete();
        }
        this.frames.clear();
        this.staging.delete();
    }

    // Reclaims staging space whose fences have signalled
    private void releaseCompletedFrames() {
        while (!this.frames.isEmpty()) {
            // Frames were fenced in submission order, so the first unsignalled one means every later one is too
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

    // The staging ring filled inside one frame (many chunks finishing at once): push what is queued, then stall until earlier frames retire; a hitch beats dropping geometry
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

    // glFenceSync
    private static GlFence createFence() {
        return new GlFence(LWJGL.glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0));
    }

    // A staged copy awaiting flush
    private record PendingCopy(DeviceBuffer target, long stagingOffset, long targetOffset, long size) {}

    // A frame's staging allocations, reclaimed when its fence signals
    private record Frame(GlFence fence, LongArrayList allocations) {}
}
