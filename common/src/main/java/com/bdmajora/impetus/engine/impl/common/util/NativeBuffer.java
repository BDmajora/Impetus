package com.bdmajora.impetus.engine.impl.common.util;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMaps;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.bdmajora.impetus.lwjgl.LWJGLServiceProvider;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.bdmajora.impetus.lwjgl.LWJGLServiceProvider.LWJGL;

public class NativeBuffer {
    private static final Logger LOGGER = LogManager.getLogger(NativeBuffer.class);

    private static final ReferenceQueue<NativeBuffer> RECLAIM_QUEUE = new ReferenceQueue<>();
    private static final Reference2ReferenceMap<Reference<NativeBuffer>, BufferReference> ACTIVE_BUFFERS =
            Reference2ReferenceMaps.synchronize(new Reference2ReferenceOpenHashMap<>());

    // Touched from every chunk worker as well as the render thread, so the counter is atomic
    private static final AtomicLong ALLOCATED = new AtomicLong();

    private final BufferReference ref;

    public static boolean ENABLE_MEMORY_TRACING = false;

    public NativeBuffer(int capacity) {
        this.ref = allocate(capacity);

        ACTIVE_BUFFERS.put(new PhantomReference<>(this, RECLAIM_QUEUE), this.ref);
    }

    // Allocates and copies src's remaining bytes
    public static NativeBuffer copy(ByteBuffer src) {
        NativeBuffer dst = new NativeBuffer(src.remaining());
        LWJGL.memCopy(src, dst.getDirectBuffer());
        return dst;
    }

    // The backing direct buffer; throws once freed
    public ByteBuffer getDirectBuffer() {
        this.ref.checkFreed();

        return LWJGL.memByteBuffer(this.ref.address, this.ref.length);
    }

    // Releases immediately rather than waiting for the cleaner
    public void free() {
        deallocate(this.ref);
    }

    // Capacity in bytes
    public int getLength() {
        return this.ref.length;
    }

    // Frees every buffer whose owner was collected; forceGc runs a GC first
    public static void reclaim(boolean forceGc) {
        if (forceGc) {
            System.gc();
        }

        Reference<? extends NativeBuffer> ref;

        while ((ref = RECLAIM_QUEUE.poll()) != null) {
            BufferReference buf = ACTIVE_BUFFERS.remove(ref);

            if (buf.freed) {
                continue;
            }

            deallocate(buf);

            if (buf.allocationSite != null) {
                LOGGER.warn("Reclaimed {} bytes at address {} that were leaked from allocation site:\n{}",
                        buf.length, buf.address,
                        Arrays.stream(buf.allocationSite)
                                .map(StackTraceElement::toString)
                                .collect(Collectors.joining("\n")));
            } else {
                LOGGER.warn("Reclaimed {} bytes at address {} that were leaked from an unknown location (logging is disabled)",
                        buf.length, buf.address);
            }
        }
    }

    // Live native bytes, for the debug screen
    public static long getTotalAllocated() {
        return ALLOCATED.get();
    }

    // Allocation site, kept only when leak tracking is on
    private static StackTraceElement[] getStackTrace() {
        return ENABLE_MEMORY_TRACING ? Thread.currentThread().getStackTrace() : null;
    }

    private static final int MAX_ALLOCATION_ATTEMPTS = 3;

    // Allocates off-heap and registers a phantom reference so a leak is still freed
    private static BufferReference allocate(int bytes) {
        long address = 0;
        int attempts = 0;

        while (++attempts <= MAX_ALLOCATION_ATTEMPTS) {
            address = LWJGL.nmemAlloc(bytes);

            if (address != LWJGLServiceProvider.NULL) {
                break;
            }

            LOGGER.error("EMERGENCY: Tried to allocate {} bytes but the allocator reports failure", bytes);
            LOGGER.error("EMERGENCY: ... Attempting to force a garbage collection cycle (attempt {}/{})", attempts, MAX_ALLOCATION_ATTEMPTS);

            // If memory allocation fails, force a garbage collection
            reclaim(true);
        }

        if (address == LWJGLServiceProvider.NULL) {
            throw new OutOfMemoryError("Couldn't allocate %s bytes after %s attempts".formatted(bytes, attempts));
        }

        StackTraceElement[] stackTrace = getStackTrace();

        BufferReference ref = new BufferReference(address, bytes, stackTrace);
        ALLOCATED.addAndGet(ref.length);

        return ref;
    }

    // Frees once, tolerant of a double call
    private static void deallocate(BufferReference ref) {
        ref.checkFreed();
        ref.freed = true;

        LWJGL.nmemFree(ref.address);

        ALLOCATED.addAndGet(-ref.length);
    }

    private static class BufferReference {
        public final long address;
        public final int length;

        public final StackTraceElement[] allocationSite;

        public boolean freed;

        private BufferReference(long address, int length, StackTraceElement[] allocationSite) {
            this.address = address;
            this.length = length;
            this.allocationSite = allocationSite;
        }

        // Throws on use after free
        private void checkFreed() {
            if (this.freed) {
                throw new IllegalStateException("Buffer has been deleted");
            }
        }
    }
}
