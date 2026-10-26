package com.bdmajora.impetus.engine.impl.common.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.system.MemoryUtil;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.nio.ByteBuffer;

// Block of native memory that is freed explicitly or reclaimed after its owner is collected
public class NativeBuffer {
    // Logger for allocation failures and reclaimed leaks
    private static final Logger LOGGER = LogManager.getLogger(NativeBuffer.class);

    // Number of allocation attempts before an OutOfMemoryError
    private static final int MAX_ALLOCATION_ATTEMPTS = 3;

    // Queue that receives the tracker of each buffer whose owner was collected
    private static final ReferenceQueue<NativeBuffer> RECLAIM_QUEUE = new ReferenceQueue<>();

    // Sentinel of the circular list of live trackers and lock for that list and the byte total
    private static final BufferReference LIVE = new BufferReference();

    // Total capacity of all live buffers in bytes
    private static volatile long allocatedBytes;

    // Switch that records the allocation site of each new buffer
    public static boolean ENABLE_MEMORY_TRACING = false;

    // Tracker that owns the native memory of this buffer
    private final BufferReference ref;

    // Creates a buffer of the given capacity in bytes and rejects a negative capacity
    public NativeBuffer(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Negative buffer capacity " + capacity);
        }

        Throwable allocationSite = ENABLE_MEMORY_TRACING ? new Throwable() : null;

        this.ref = new BufferReference(this, allocate(capacity), capacity, allocationSite);
    }

    // Creates a buffer that holds the remaining bytes of the source and frees it again when the source cannot be read
    public static NativeBuffer copy(ByteBuffer src) {
        int length = src.remaining();
        NativeBuffer dst = new NativeBuffer(length);

        try {
            MemoryUtil.memByteBuffer(dst.ref.address, length).put(0, src, src.position(), length);
        } catch (Throwable t) {
            dst.free();
            throw t;
        }

        return dst;
    }

    // Returns a new view of the whole buffer in native byte order and rejects a freed buffer
    public ByteBuffer getDirectBuffer() {
        BufferReference ref = this.ref;

        if (ref.freed) {
            throw new IllegalStateException("Buffer has been deleted");
        }

        return MemoryUtil.memByteBuffer(ref.address, ref.length);
    }

    // Frees the native memory and rejects a buffer that was already freed
    public void free() {
        if (!release(this.ref)) {
            throw new IllegalStateException("Buffer has been deleted");
        }

        // Keeps this buffer reachable until its memory is released
        Reference.reachabilityFence(this);
    }

    // Returns the capacity in bytes
    public int getLength() {
        return this.ref.length;
    }

    // Frees every buffer whose owner was collected and forces a garbage collection first when forceGc is set
    public static void reclaim(boolean forceGc) {
        if (forceGc) {
            System.gc();
        }

        Reference<? extends NativeBuffer> polled;

        while ((polled = RECLAIM_QUEUE.poll()) != null) {
            BufferReference ref = (BufferReference) polled;

            if (!release(ref)) {
                continue;
            }

            if (ref.allocationSite != null) {
                LOGGER.warn("Reclaimed {} bytes at address {} that were leaked from allocation site:\n{}",
                        ref.length, ref.address, describe(ref.allocationSite));
            } else {
                LOGGER.warn("Reclaimed {} bytes at address {} that were leaked from an unknown location (logging is disabled)",
                        ref.length, ref.address);
            }
        }
    }

    // Returns the total capacity of all live buffers in bytes
    public static long getTotalAllocated() {
        return allocatedBytes;
    }

    // Allocates at least one byte of native memory and forces a garbage collection after each failed attempt
    private static long allocate(int bytes) {
        long size = Math.max(bytes, 1);

        for (int attempt = 1; attempt <= MAX_ALLOCATION_ATTEMPTS; attempt++) {
            long address = MemoryUtil.nmemAlloc(size);

            if (address != MemoryUtil.NULL) {
                return address;
            }

            LOGGER.error("EMERGENCY: Tried to allocate {} bytes but the allocator reports failure", bytes);
            LOGGER.error("EMERGENCY: ... Attempting to force a garbage collection cycle (attempt {}/{})", attempt, MAX_ALLOCATION_ATTEMPTS);

            reclaim(true);
        }

        throw new OutOfMemoryError("Couldn't allocate %s bytes after %s attempts".formatted(bytes, MAX_ALLOCATION_ATTEMPTS));
    }

    // Releases the native memory of a tracker once and returns false when it was already released
    private static boolean release(BufferReference ref) {
        synchronized (LIVE) {
            if (ref.freed) {
                return false;
            }

            ref.freed = true;
            ref.prev.next = ref.next;
            ref.next.prev = ref.prev;
            ref.prev = null;
            ref.next = null;
            allocatedBytes -= ref.length;
        }

        MemoryUtil.nmemFree(ref.address);

        return true;
    }

    // Joins the frames of an allocation site with line breaks
    private static String describe(Throwable allocationSite) {
        StackTraceElement[] frames = allocationSite.getStackTrace();

        if (frames.length == 0) {
            return "";
        }

        StringBuilder text = new StringBuilder().append(frames[0]);

        for (int i = 1; i < frames.length; i++) {
            text.append('\n').append(frames[i]);
        }

        return text.toString();
    }

    // Phantom reference that tracks the native memory of one buffer
    private static final class BufferReference extends PhantomReference<NativeBuffer> {
        // Start address of the native memory
        private final long address;

        // Capacity of the buffer in bytes
        private final int length;

        // Throwable created at the allocation site or null when tracing is off
        private final Throwable allocationSite;

        // Flag set once the native memory is released
        private boolean freed;

        // Previous tracker in the list of live trackers or null once released
        private BufferReference prev;

        // Next tracker in the list of live trackers or null once released
        private BufferReference next;

        // Creates the list sentinel
        private BufferReference() {
            super(null, null);

            this.address = MemoryUtil.NULL;
            this.length = 0;
            this.allocationSite = null;
            this.prev = this;
            this.next = this;
        }

        // Creates a tracker for the owner and links it into the list of live trackers
        private BufferReference(NativeBuffer owner, long address, int length, Throwable allocationSite) {
            super(owner, RECLAIM_QUEUE);

            this.address = address;
            this.length = length;
            this.allocationSite = allocationSite;

            synchronized (LIVE) {
                BufferReference first = LIVE.next;

                this.prev = LIVE;
                this.next = first;
                first.prev = this;
                LIVE.next = this;
                allocatedBytes += length;
            }
        }
    }
}
