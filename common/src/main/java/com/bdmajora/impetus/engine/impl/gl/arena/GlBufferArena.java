package com.bdmajora.impetus.engine.impl.gl.arena;

import com.bdmajora.impetus.engine.impl.gl.arena.staging.StagingBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferUsage;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;

import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.TreeSet;

// Suballocator that packs segments of whole elements into one GL buffer and grows or compacts that buffer on demand
public class GlBufferArena {
    // Switch that runs the segment list checks after every change
    static final boolean CHECK_ASSERTIONS = false;

    // Usage hint of every backing buffer
    private static final GlBufferUsage BUFFER_USAGE = GlBufferUsage.STATIC_DRAW;

    // Size in bytes from which growth adds half the current size instead of doubling it
    private static final int LARGE_ARENA_THRESHOLD_BYTES = 4 * 1024 * 1024;

    // Divisor of the growth step of a large arena
    private static final int LARGE_GROWTH_FACTOR = 2;

    // Order of free segments by length and then by offset
    private static final Comparator<GlBufferSegment> BEST_FIT_ORDER = GlBufferArena::compareBestFit;

    // Staging path of every upload
    private final StagingBuffer stagingBuffer;

    // Free segments of the list in best fit order
    private final TreeSet<GlBufferSegment> freeSegments = new TreeSet<>(BEST_FIT_ORDER);

    // Search key of the best fit lookup that sits at offset zero and belongs to no arena
    private final GlBufferSegment probe = new GlBufferSegment(null, 0, 1);

    // Backing GL buffer
    private GlMutableBuffer arenaBuffer;

    // First segment by offset or null when no segment is linked
    private GlBufferSegment head;

    // Capacity in elements
    private int capacity;

    // Elements held by live segments
    private int used;

    // Flag set by the first delete
    private boolean deleted;

    // Size of one element in bytes
    private final int stride;

    // Smallest capacity in elements that growth selects
    private final int minimumCapacity;

    // Creates an arena with an empty backing buffer and rejects null arguments and a stride or minimum capacity out of range
    public GlBufferArena(CommandList commands, int stride, int minimumCapacityBytes, StagingBuffer stagingBuffer) {
        Objects.requireNonNull(commands, "Command list must not be null");
        Objects.requireNonNull(stagingBuffer, "Staging buffer must not be null");

        if (stride <= 0) {
            throw new IllegalArgumentException("Stride must be positive");
        }

        if (minimumCapacityBytes < 0) {
            throw new IllegalArgumentException("Minimum capacity must not be negative");
        }

        this.stride = stride;
        this.minimumCapacity = this.elementsFor(minimumCapacityBytes);
        this.stagingBuffer = stagingBuffer;
        this.arenaBuffer = createBuffer(commands, 0L);
    }

    // Creates a buffer with storage of the given size in bytes and deletes it again when the storage allocation throws
    private static GlMutableBuffer createBuffer(CommandList commands, long bytes) {
        GlMutableBuffer buffer = commands.createMutableBuffer();

        try {
            commands.allocateStorage(buffer, bytes, BUFFER_USAGE);
        } catch (Throwable t) {
            commands.deleteBuffer(buffer);
            throw t;
        }

        return buffer;
    }

    // Moves the live segments behind one free segment at the start of a new buffer and leaves the arena unchanged when a GL call throws
    private void resize(CommandList commands, int newCapacity) {
        int freeLength = newCapacity - this.used;
        GlMutableBuffer src = this.arenaBuffer;
        GlMutableBuffer dst = createBuffer(commands, (long) newCapacity * this.stride);

        try {
            this.copyLiveSegments(commands, src, dst, freeLength);
        } catch (Throwable t) {
            commands.deleteBuffer(dst);
            throw t;
        }

        this.relinkLiveSegments(freeLength);
        this.arenaBuffer = dst;
        this.capacity = newCapacity;

        commands.deleteBuffer(src);

        this.checkAssertions();
    }

    // Copies the live segments to the destination from the given element offset and merges each run of adjacent live segments into one copy
    private void copyLiveSegments(CommandList commands, GlBuffer src, GlBuffer dst, int writeOffset) {
        long stride = this.stride;
        int runOffset = 0;
        int runLength = 0;

        for (GlBufferSegment seg = this.head; seg != null; seg = seg.getNext()) {
            if (!seg.isFree()) {
                if (runLength == 0) {
                    runOffset = seg.getOffset();
                }

                runLength += seg.getLength();
            } else if (runLength != 0) {
                commands.copyBufferSubData(src, dst, runOffset * stride, writeOffset * stride, runLength * stride);
                writeOffset += runLength;
                runLength = 0;
            }
        }

        if (runLength != 0) {
            commands.copyBufferSubData(src, dst, runOffset * stride, writeOffset * stride, runLength * stride);
        }
    }

    // Rebuilds the segment list as one free segment of the given length followed by the live segments in offset order
    private void relinkLiveSegments(int freeLength) {
        GlBufferSegment first = null;
        GlBufferSegment last = null;

        this.freeSegments.clear();

        if (freeLength > 0) {
            first = last = new GlBufferSegment(this, 0, freeLength);
            first.setFree(true);
            this.freeSegments.add(first);
        }

        int writeOffset = freeLength;
        GlBufferSegment seg = this.head;

        while (seg != null) {
            GlBufferSegment next = seg.getNext();

            if (!seg.isFree()) {
                seg.setOffset(writeOffset);
                seg.setPrev(last);
                writeOffset += seg.getLength();

                if (last == null) {
                    first = seg;
                } else {
                    last.setNext(seg);
                }

                last = seg;
            }

            seg = next;
        }

        if (last != null) {
            last.setNext(null);
        }

        this.head = first;
    }

    // Returns the bytes held by live segments
    public long getDeviceUsedMemoryL() {
        return (long) this.used * this.stride;
    }

    // Returns the size of the backing buffer in bytes
    public long getDeviceAllocatedMemoryL() {
        return (long) this.capacity * this.stride;
    }

    // Returns a live segment carved from the end of the best fitting free segment or null when no free segment is large enough
    private GlBufferSegment alloc(int size) {
        // Gives a zero length request its own segment outside the list
        if (size == 0) {
            return new GlBufferSegment(this, 0, 0);
        }

        GlBufferSegment probe = this.probe;
        probe.setLength(size);

        GlBufferSegment free = this.freeSegments.ceiling(probe);

        if (free == null) {
            return null;
        }

        this.freeSegments.remove(free);

        GlBufferSegment result;

        if (free.getLength() == size) {
            free.setFree(false);
            result = free;
        } else {
            GlBufferSegment next = free.getNext();

            result = new GlBufferSegment(this, free.getEnd() - size, size);
            result.setNext(next);
            result.setPrev(free);

            if (next != null) {
                next.setPrev(result);
            }

            free.setLength(free.getLength() - size);
            free.setNext(result);
            this.freeSegments.add(free);
        }

        this.used += size;
        this.checkAssertions();

        return result;
    }

    // Orders two segments by length and then by offset
    private static int compareBestFit(GlBufferSegment a, GlBufferSegment b) {
        int order = Integer.compare(a.getLength(), b.getLength());

        return order != 0 ? order : Integer.compare(a.getOffset(), b.getOffset());
    }

    // Marks the segment free and merges it with free neighbours and rejects a segment that is already free
    public void free(GlBufferSegment entry) {
        if (entry.isFree()) {
            throw new IllegalStateException("Already freed");
        }

        entry.setFree(true);

        int length = entry.getLength();

        // Leaves the list alone for a deleted arena or a zero length segment
        if (this.deleted || length == 0) {
            return;
        }

        this.used -= length;

        GlBufferSegment next = entry.getNext();

        if (next != null && next.isFree()) {
            this.freeSegments.remove(next);
            entry.mergeInto(next);
        }

        GlBufferSegment prev = entry.getPrev();

        if (prev != null && prev.isFree()) {
            this.freeSegments.remove(prev);
            prev.mergeInto(entry);
            entry = prev;
        }

        this.freeSegments.add(entry);
        this.checkAssertions();
    }

    // Deletes the backing buffer and empties the arena on the first call and ignores later calls
    public void delete(CommandList commands) {
        Objects.requireNonNull(commands, "Command list must not be null");

        if (this.deleted) {
            return;
        }

        this.deleted = true;
        this.head = null;
        this.freeSegments.clear();
        this.capacity = 0;
        this.used = 0;

        commands.deleteBuffer(this.arenaBuffer);
    }

    // Returns whether delete has run
    public boolean isDeleted() {
        return this.deleted;
    }

    // Returns whether no live segment holds any element
    public boolean isEmpty() {
        return this.used == 0;
    }

    // Returns the backing buffer
    public GlBuffer getBufferObject() {
        return this.arenaBuffer;
    }

    // Places every upload of the queue and grows the arena once when some do not fit and returns whether the backing buffer changed
    public boolean upload(CommandList commandList, List<PendingUpload> queue) {
        Objects.requireNonNull(commandList, "Command list must not be null");
        Objects.requireNonNull(queue, "Upload queue must not be null");
        this.checkNotDeleted();

        GlBuffer buffer = this.arenaBuffer;

        this.tryUploads(commandList, queue);

        if (!queue.isEmpty()) {
            // Compacts or grows the arena into one free segment that holds every remaining upload
            this.reserve(commandList, this.totalElements(queue));
            this.tryUploads(commandList, queue);

            if (!queue.isEmpty()) {
                throw new RuntimeException("Failed to upload all buffers");
            }
        }

        return this.arenaBuffer != buffer;
    }

    // Places every upload that fits and keeps the others in the queue in order and then flushes the staging buffer
    private void tryUploads(CommandList commandList, List<PendingUpload> queue) {
        ListIterator<PendingUpload> reader = queue.listIterator();
        ListIterator<PendingUpload> writer = queue.listIterator();
        int kept = 0;
        int done = 0;

        try {
            while (reader.hasNext()) {
                PendingUpload upload = reader.next();

                if (!this.tryUpload(commandList, upload)) {
                    writer.next();
                    writer.set(upload);
                    kept++;
                }

                done++;
            }
        } finally {
            // Removes the placed uploads and keeps a failing upload and every later one
            queue.subList(kept, done).clear();
        }

        this.stagingBuffer.flush(commandList);
    }

    // Allocates a segment and stages the data of the upload and returns false when no free segment is large enough
    private boolean tryUpload(CommandList commandList, PendingUpload upload) {
        ByteBuffer data = upload.getDataBuffer()
                .getDirectBuffer();

        GlBufferSegment dst = this.alloc(this.elementsFor(data.remaining()));

        if (dst == null) {
            return false;
        }

        try {
            this.stagingBuffer.enqueueCopy(commandList, data, this.arenaBuffer, (long) dst.getOffset() * this.stride);
            upload.setResult(dst);
        } catch (Throwable t) {
            this.free(dst);
            throw t;
        }

        return true;
    }

    // Returns the number of elements that hold the given number of bytes
    private int elementsFor(int byteLength) {
        return Math.ceilDiv(byteLength, this.stride);
    }

    // Returns the total number of elements of the uploads in the queue
    private long totalElements(List<PendingUpload> queue) {
        long total = 0L;

        for (PendingUpload upload : queue) {
            total += this.elementsFor(upload.getLength());
        }

        return total;
    }

    // Compacts or grows the arena so the given number of elements fits in one free segment and rejects a negative count or a deleted arena
    public void ensureCapacity(CommandList commandList, int elementCount) {
        Objects.requireNonNull(commandList, "Command list must not be null");

        if (elementCount < 0) {
            throw new IllegalArgumentException("Element count must not be negative");
        }

        this.checkNotDeleted();
        this.reserve(commandList, elementCount);
    }

    // Compacts the arena when the live and requested elements fill at most half of it and grows it otherwise
    void reserve(CommandList commandList, long elementCount) {
        long required = this.used + elementCount;

        this.resize(commandList, required <= this.capacity >> 1 ? this.capacity : this.growthTargetFor(required));
    }

    // Returns the capacity in elements that growth selects for the required elements and throws when they exceed the int range
    private int growthTargetFor(long required) {
        if (required > Integer.MAX_VALUE) {
            throw new OutOfMemoryError("Arena cannot grow beyond " + Integer.MAX_VALUE + " elements");
        }

        long base = Math.max(required, this.capacity);
        long increment = base * this.stride < LARGE_ARENA_THRESHOLD_BYTES ? base : base / LARGE_GROWTH_FACTOR;

        return (int) Math.min(Math.max(base + increment, this.minimumCapacity), Integer.MAX_VALUE);
    }

    // Rejects any use of a deleted arena
    private void checkNotDeleted() {
        if (this.deleted) {
            throw new IllegalStateException("Arena has been deleted");
        }
    }

    // Runs the segment list checks when the switch is on
    private void checkAssertions() {
        if (CHECK_ASSERTIONS) {
            this.checkAssertions0();
        }
    }

    // Throws when the segment list or the free index breaks an invariant
    private void checkAssertions0() {
        GlBufferSegment prev = null;
        int end = 0;
        int live = 0;
        int free = 0;

        for (GlBufferSegment seg = this.head; seg != null; seg = seg.getNext()) {
            if (seg.getPrev() != prev) {
                throw new IllegalStateException("segment.prev is not the previous segment: broken links");
            }

            if (seg.getOffset() != end) {
                throw new IllegalStateException("segment.start != segment.prev.end: overlapping or sparse segments");
            }

            if (seg.getLength() <= 0) {
                throw new IllegalStateException("segment.length <= 0: empty segment in the list");
            }

            if (seg.isFree()) {
                if (prev != null && prev.isFree()) {
                    throw new IllegalStateException("segment.free && segment.prev.free: not merged consecutive segments");
                }

                if (!this.freeSegments.contains(seg)) {
                    throw new IllegalStateException("segment.free but not indexed: free index out of date");
                }

                free++;
            } else {
                live += seg.getLength();
            }

            end = seg.getEnd();
            prev = seg;
        }

        if (end != this.capacity) {
            throw new IllegalStateException("segment.end != arena.capacity: segments do not cover the arena");
        }

        if (free != this.freeSegments.size()) {
            throw new IllegalStateException("free index holds segments outside the list");
        }

        if (live != this.used) {
            throw new IllegalStateException("arena.used is invalid");
        }
    }
}
