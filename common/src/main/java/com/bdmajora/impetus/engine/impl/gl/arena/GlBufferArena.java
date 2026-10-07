package com.bdmajora.impetus.engine.impl.gl.arena;

import com.bdmajora.impetus.engine.impl.gl.arena.staging.StagingBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferUsage;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GlBufferArena {
    static final boolean CHECK_ASSERTIONS = false;

    private static final GlBufferUsage BUFFER_USAGE = GlBufferUsage.STATIC_DRAW;

    private static final int LARGE_ARENA_THRESHOLD_BYTES = 4 * 1024 * 1024;

    private static final int LARGE_GROWTH_FACTOR = 2;

    private final StagingBuffer stagingBuffer;
    private GlMutableBuffer arenaBuffer;

    private GlBufferSegment head;

    private int capacity;
    private int used;

    private final int stride;
    private final int minimumCapacity;

    public GlBufferArena(CommandList commands, int stride, int minimumCapacityBytes, StagingBuffer stagingBuffer) {
        if (stride <= 0) {
            throw new IllegalArgumentException("Stride must be positive");
        }

        this.stride = stride;
        this.minimumCapacity = this.elementsFor(minimumCapacityBytes);

        this.capacity = 0;
        this.head = null;

        this.arenaBuffer = commands.createMutableBuffer();
        commands.allocateStorage(this.arenaBuffer, 0L, BUFFER_USAGE);

        this.stagingBuffer = stagingBuffer;
    }

    // Allocates a larger buffer and compacts every live segment into it
    private void resize(CommandList commandList, int newCapacity) {
        if (this.used > newCapacity) {
            throw new UnsupportedOperationException("New capacity must be larger than used size");
        }

        this.checkAssertions();

        int tail = newCapacity - this.used;

        List<GlBufferSegment> usedSegments = this.getUsedSegments();
        List<PendingBufferCopyCommand> pendingCopies = this.buildTransferList(usedSegments, tail);

        this.transferSegments(commandList, pendingCopies, newCapacity);

        if (tail == 0) {
            this.head = usedSegments.isEmpty() ? null : usedSegments.get(0);

            if (this.head != null) {
                this.head.setPrev(null);
            }
        } else {
            this.head = new GlBufferSegment(this, 0, tail);
            this.head.setFree(true);

            if (usedSegments.isEmpty()) {
                this.head.setNext(null);
            } else {
                this.head.setNext(usedSegments.get(0));
                this.head.getNext()
                        .setPrev(this.head);
            }
        }

        this.checkAssertions();
    }

    // Plans the compaction copies, merging adjacent segments into one copy
    private List<PendingBufferCopyCommand> buildTransferList(List<GlBufferSegment> usedSegments, int base) {
        List<PendingBufferCopyCommand> pendingCopies = new ArrayList<>();
        PendingBufferCopyCommand currentCopyCommand = null;

        int writeOffset = base;

        for (int i = 0; i < usedSegments.size(); i++) {
            GlBufferSegment s = usedSegments.get(i);

            if (currentCopyCommand == null || currentCopyCommand.readOffset + currentCopyCommand.length != s.getOffset()) {
                if (currentCopyCommand != null) {
                    pendingCopies.add(currentCopyCommand);
                }

                currentCopyCommand = new PendingBufferCopyCommand(s.getOffset(), writeOffset, s.getLength());
            } else {
                currentCopyCommand.length += s.getLength();
            }

            s.setOffset(writeOffset);

            if (i + 1 < usedSegments.size()) {
                s.setNext(usedSegments.get(i + 1));
            } else {
                s.setNext(null);
            }

            if (i - 1 < 0) {
                s.setPrev(null);
            } else {
                s.setPrev(usedSegments.get(i - 1));
            }

            writeOffset += s.getLength();
        }

        if (currentCopyCommand != null) {
            pendingCopies.add(currentCopyCommand);
        }

        return pendingCopies;
    }

    // Executes the planned copies into a fresh buffer and swaps it in
    private void transferSegments(CommandList commandList, Collection<PendingBufferCopyCommand> list, int capacity) {
        GlMutableBuffer srcBufferObj = this.arenaBuffer;
        GlMutableBuffer dstBufferObj = commandList.createMutableBuffer();

        commandList.allocateStorage(dstBufferObj, (long)capacity * this.stride, BUFFER_USAGE);

        for (PendingBufferCopyCommand cmd : list) {
            commandList.copyBufferSubData(srcBufferObj, dstBufferObj,
                    (long)cmd.readOffset * this.stride,
                    (long)cmd.writeOffset * this.stride,
                    (long)cmd.length * this.stride);
        }

        commandList.deleteBuffer(srcBufferObj);

        this.arenaBuffer = dstBufferObj;
        this.capacity = capacity;
    }

    // Live segments in address order
    private ArrayList<GlBufferSegment> getUsedSegments() {
        ArrayList<GlBufferSegment> used = new ArrayList<>();
        GlBufferSegment seg = this.head;

        while (seg != null) {
            GlBufferSegment next = seg.getNext();

            if (!seg.isFree()) {
                used.add(seg);
            }

            seg = next;
        }

        return used;
    }

    // Long form
    public long getDeviceUsedMemoryL() {
        return (long)this.used * this.stride;
    }

    // Long form
    public long getDeviceAllocatedMemoryL() {
        return (long)this.capacity * this.stride;
    }

    // Carves from a free segment, splitting it if larger; null when nothing fits
    private GlBufferSegment alloc(int size) {
        GlBufferSegment a = this.findFree(size);

        if (a == null) {
            return null;
        }

        GlBufferSegment result;

        if (a.getLength() == size) {
            a.setFree(false);

            result = a;
        } else {
            GlBufferSegment b = new GlBufferSegment(this, a.getEnd() - size, size);
            b.setNext(a.getNext());
            b.setPrev(a);

            if (b.getNext() != null) {
                b.getNext()
                        .setPrev(b);
            }

            a.setLength(a.getLength() - size);
            a.setNext(b);

            result = b;
        }

        this.used += result.getLength();
        this.checkAssertions();

        return result;
    }

    // Best-fit walk of the whole segment list; an exact match returns immediately
    private GlBufferSegment findFree(int size) {
        GlBufferSegment entry = this.head;
        GlBufferSegment best = null;

        while (entry != null) {
            if (entry.isFree()) {
                if (entry.getLength() == size) {
                    return entry;
                } else if (entry.getLength() >= size) {
                    if (best == null || best.getLength() > entry.getLength()) {
                        best = entry;
                    }
                }
            }

            entry = entry.getNext();
        }

        return best;
    }

    // Returns a segment and coalesces with free neighbours
    public void free(GlBufferSegment entry) {
        if (entry.isFree()) {
            throw new IllegalStateException("Already freed");
        }

        entry.setFree(true);

        this.used -= entry.getLength();

        GlBufferSegment next = entry.getNext();

        if (next != null && next.isFree()) {
            entry.mergeInto(next);
        }

        GlBufferSegment prev = entry.getPrev();

        if (prev != null && prev.isFree()) {
            prev.mergeInto(entry);
        }

        this.checkAssertions();
    }

    // Frees the GL buffer
    public void delete(CommandList commands) {
        commands.deleteBuffer(this.arenaBuffer);
        this.capacity = -1;
    }

    // Whether delete has run
    public boolean isDeleted() {
        return this.capacity < 0;
    }

    // No live segments
    public boolean isEmpty() {
        return this.used <= 0;
    }

    // The backing buffer, for binding
    public GlBuffer getBufferObject() {
        return this.arenaBuffer;
    }

    // Allocates and uploads each pending buffer, growing the arena when it fills; true if it grew; the queue is consumed (emptied) by the call
    public boolean upload(CommandList commandList, List<PendingUpload> queue) {
        // Record the buffer object first so a re-allocation during the upload can be detected and reported through the return flag
        GlBuffer buffer = this.arenaBuffer;

        // Try to upload all of the data into free segments first
        this.tryUploads(commandList, queue);

        // If we weren't able to upload some buffers, they will have been left behind in the queue
        if (!queue.isEmpty()) {
            // Calculate the amount of memory needed for the remaining uploads
            int remainingElements = (int)queue.stream()
                    .mapToLong(upload -> this.elementsFor(upload.getDataBuffer().getLength()))
                    .sum();

            // Grow the arena for the remaining uploads; the re-allocation compacts, leaving one continuous free segment
            this.ensureCapacity(commandList, remainingElements);

            // Try again to upload any buffers that failed last time
            this.tryUploads(commandList, queue);

            // If we still had failures, something has gone wrong
            if (!queue.isEmpty()) {
                throw new RuntimeException("Failed to upload all buffers");
            }
        }

        return this.arenaBuffer != buffer;
    }

    // Uploads everything that fits, leaving the rest in the queue
    private void tryUploads(CommandList commandList, List<PendingUpload> queue) {
        queue.removeIf(upload -> this.tryUpload(commandList, upload));
        this.stagingBuffer.flush(commandList);
    }

    // One allocation and staged copy; false when the arena is full
    private boolean tryUpload(CommandList commandList, PendingUpload upload) {
        ByteBuffer data = upload.getDataBuffer()
                .getDirectBuffer();

        int elementCount = this.elementsFor(data.remaining());

        GlBufferSegment dst = this.alloc(elementCount);

        if (dst == null) {
            return false;
        }

        // Copy the data into our staging buffer, then copy it into the arena's buffer
        this.stagingBuffer.enqueueCopy(commandList, data, this.arenaBuffer, (long)dst.getOffset() * this.stride);

        upload.setResult(dst);

        return true;
    }

    private int elementsFor(int byteLength) {
        return (int)(((long)byteLength + this.stride - 1) / this.stride);
    }

    public void ensureCapacity(CommandList commandList, int elementCount) {
        long required = (long)this.used + elementCount;

        if (required * 2 <= this.capacity) {
            this.resize(commandList, this.capacity);
            return;
        }

        this.resize(commandList, this.growthTargetFor(required));
    }

    private int growthTargetFor(long required) {
        long base = Math.max(required, this.capacity);
        long baseBytes = base * this.stride;

        long increment = baseBytes < LARGE_ARENA_THRESHOLD_BYTES ? base : base / LARGE_GROWTH_FACTOR;

        long target = Math.max(base + increment, this.minimumCapacity);

        if (required > Integer.MAX_VALUE) {
            throw new OutOfMemoryError("Arena cannot grow beyond " + Integer.MAX_VALUE + " elements");
        }

        return (int)Math.min(target, Integer.MAX_VALUE);
    }

    // Segment list invariants, debug builds only
    private void checkAssertions() {
        if (CHECK_ASSERTIONS) {
            this.checkAssertions0();
        }
    }

    // The checks themselves
    private void checkAssertions0() {
        GlBufferSegment seg = this.head;
        int used = 0;

        while (seg != null) {
            if (seg.getOffset() < 0) {
                throw new IllegalStateException("segment.start < 0: out of bounds");
            } else if (seg.getEnd() > this.capacity) {
                throw new IllegalStateException("segment.end > arena.capacity: out of bounds");
            }

            if (!seg.isFree()) {
                used += seg.getLength();
            }

            GlBufferSegment next = seg.getNext();

            if (next != null) {
                if (next.getOffset() < seg.getEnd()) {
                    throw new IllegalStateException("segment.next.start < segment.end: overlapping segments (corrupted)");
                } else if (next.getOffset() > seg.getEnd()) {
                    throw new IllegalStateException("segment.next.start > segment.end: not truly connected (sparsity error)");
                }

                if (next.isFree() && next.getNext() != null) {
                    if (next.getNext().isFree()) {
                        throw new IllegalStateException("segment.free && segment.next.free: not merged consecutive segments");
                    }
                }
            }

            GlBufferSegment prev = seg.getPrev();

            if (prev != null) {
                if (prev.getEnd() > seg.getOffset()) {
                    throw new IllegalStateException("segment.prev.end > segment.start: overlapping segments (corrupted)");
                } else if (prev.getEnd() < seg.getOffset()) {
                    throw new IllegalStateException("segment.prev.end < segment.start: not truly connected (sparsity error)");
                }

                if (prev.isFree() && prev.getPrev() != null) {
                    if (prev.getPrev().isFree()) {
                        throw new IllegalStateException("segment.free && segment.prev.free: not merged consecutive segments");
                    }
                }
            }

            seg = next;
        }

        if (this.used < 0) {
            throw new IllegalStateException("arena.used < 0: failure to track");
        } else if (this.used > this.capacity) {
            throw new IllegalStateException("arena.used > arena.capacity: failure to track");
        }

        if (this.used != used) {
            throw new IllegalStateException("arena.used is invalid");
        }
    }

}
