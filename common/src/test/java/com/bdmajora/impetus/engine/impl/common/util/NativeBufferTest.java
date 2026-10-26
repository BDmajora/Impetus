package com.bdmajora.impetus.engine.impl.common.util;

import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.foreign.Arena;
import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

// Unit tests for every method of NativeBuffer
class NativeBufferTest {
    // Capacities on the byte and word and page boundaries
    private static final int[] CAPACITIES = {1, 2, 3, 4, 7, 8, 9, 15, 16, 17, 63, 64, 65, 4095, 4096, 4097, 1 << 20};

    // Turns memory tracing off after each test
    @AfterEach
    void disableTracing() {
        NativeBuffer.ENABLE_MEMORY_TRACING = false;
    }

    // Opens a static mock of MemoryUtil that calls the real methods
    private static MockedStatic<MemoryUtil> memory() {
        return memory(size -> null);
    }

    // Opens a static mock of MemoryUtil whose nmemAlloc returns the stand in address or calls the real allocator when it is null
    private static MockedStatic<MemoryUtil> memory(LongFunction<Long> standIn) {
        return Mockito.mockStatic(MemoryUtil.class, invocation -> {
            if (invocation.getMethod().getName().equals("nmemAlloc")) {
                Long address = standIn.apply(invocation.<Long>getArgument(0));

                if (address != null) {
                    return address;
                }
            }

            return invocation.callRealMethod();
        });
    }

    // Opens a static mock of MemoryUtil on the current thread that records each freed address and calls the real methods
    private static MockedStatic<MemoryUtil> recordingFrees(Queue<Long> freed) {
        return Mockito.mockStatic(MemoryUtil.class, invocation -> {
            if (invocation.getMethod().getName().equals("nmemFree")) {
                freed.add(invocation.getArgument(0));
            }

            return invocation.callRealMethod();
        });
    }

    // Returns the tracker that owns the native memory of a buffer
    private static Object tracker(NativeBuffer buffer) throws ReflectiveOperationException {
        Field field = NativeBuffer.class.getDeclaredField("ref");
        field.setAccessible(true);
        return field.get(buffer);
    }

    // Returns a named field of a tracker
    private static Object trackerField(Object tracker, String name) throws ReflectiveOperationException {
        Field field = tracker.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(tracker);
    }

    // Returns whether the native memory of a tracker was released
    private static boolean released(Object tracker) throws ReflectiveOperationException {
        return (boolean) trackerField(tracker, "freed");
    }

    // Creates a buffer and drops it and returns its tracker
    private static Object leak(int capacity) throws ReflectiveOperationException {
        return tracker(new NativeBuffer(capacity));
    }

    // Returns the start address of a buffer
    private static long address(NativeBuffer buffer) {
        return MemoryUtil.memAddress(buffer.getDirectBuffer());
    }

    // Returns the total of live native bytes
    private static long total() {
        return NativeBuffer.getTotalAllocated();
    }

    // Fills a buffer with bytes derived from their index and returns it
    private static ByteBuffer pattern(ByteBuffer buffer) {
        for (int i = 0; i < buffer.capacity(); i++) {
            buffer.put(i, (byte) (i * 31 + 7));
        }

        return buffer;
    }

    // Copies a source and checks the copy and the unchanged source state and frees the copy
    private static void assertCopies(ByteBuffer src) {
        int position = src.position();
        int limit = src.limit();
        ByteOrder order = src.order();
        long before = total();
        NativeBuffer copy = NativeBuffer.copy(src);
        ByteBuffer view = copy.getDirectBuffer();

        assertEquals(limit - position, copy.getLength());
        assertEquals(limit - position, view.capacity());
        assertEquals(before + copy.getLength(), total());

        for (int i = 0; i < copy.getLength(); i++) {
            assertEquals(src.get(position + i), view.get(i), "byte " + i);
        }

        assertEquals(position, src.position());
        assertEquals(limit, src.limit());
        assertEquals(order, src.order());
        copy.free();
        assertEquals(before, total());
    }

    // Checks that each capacity is allocated and counted and released exactly
    @Test
    void allocatesTheRequestedCapacity() {
        for (int capacity : CAPACITIES) {
            long before = total();
            NativeBuffer buffer = new NativeBuffer(capacity);
            ByteBuffer view = buffer.getDirectBuffer();

            assertEquals(capacity, buffer.getLength());
            assertEquals(capacity, view.capacity());
            assertEquals(before + capacity, total());
            pattern(view);
            assertEquals((byte) ((capacity - 1) * 31 + 7), view.get(capacity - 1));
            buffer.free();
            assertEquals(before, total());
        }
    }

    // Checks that a negative or overflowed capacity is rejected before the allocator runs
    @Test
    void rejectsNegativeCapacity() {
        int overflowed = 46341 * 46341;
        long before = total();

        assertTrue(overflowed < 0);

        try (MockedStatic<MemoryUtil> memory = memory()) {
            for (int capacity : new int[] {-1, -2, -4096, Integer.MIN_VALUE + 1, Integer.MIN_VALUE, overflowed}) {
                IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> new NativeBuffer(capacity));
                assertTrue(error.getMessage().contains(Integer.toString(capacity)));
            }

            memory.verify(() -> MemoryUtil.nmemAlloc(Mockito.anyLong()), Mockito.never());
        }

        assertEquals(before, total());
    }

    // Checks that an empty buffer requests one byte and works with an allocator that returns null for zero bytes
    @Test
    void emptyBufferRequestsOneByte() {
        long before = total();

        try (MockedStatic<MemoryUtil> memory = memory(size -> size == 0 ? MemoryUtil.NULL : null)) {
            NativeBuffer buffer = new NativeBuffer(0);
            ByteBuffer view = buffer.getDirectBuffer();
            long address = MemoryUtil.memAddress(view);

            memory.verify(() -> MemoryUtil.nmemAlloc(1L));
            memory.verify(() -> MemoryUtil.nmemAlloc(0L), Mockito.never());
            assertEquals(0, buffer.getLength());
            assertEquals(0, view.capacity());
            assertNotEquals(MemoryUtil.NULL, address);
            assertEquals(before, total());
            assertThrows(IndexOutOfBoundsException.class, () -> view.get(0));
            buffer.free();
            memory.verify(() -> MemoryUtil.nmemFree(address));
        }

        assertEquals(before, total());
    }

    // Checks that two empty buffers get distinct addresses that are never null
    @Test
    void emptyBuffersHaveDistinctAddresses() {
        NativeBuffer first = new NativeBuffer(0);
        NativeBuffer second = new NativeBuffer(0);

        assertNotEquals(MemoryUtil.NULL, address(first));
        assertNotEquals(MemoryUtil.NULL, address(second));
        assertNotEquals(address(first), address(second));
        first.free();
        second.free();
    }

    // Checks that two buffers at the int limit are counted in long arithmetic without overflow
    @Test
    void countsBuffersAtTheIntLimit() {
        long first = MemoryUtil.nmemAlloc(16);
        long second = MemoryUtil.nmemAlloc(16);
        long[] standIns = {first, second};
        AtomicInteger next = new AtomicInteger();
        long before = total();

        try (MockedStatic<MemoryUtil> memory = memory(size -> size == Integer.MAX_VALUE ? standIns[next.getAndIncrement()] : null)) {
            NativeBuffer a = new NativeBuffer(Integer.MAX_VALUE);
            NativeBuffer b = new NativeBuffer(Integer.MAX_VALUE);

            assertEquals(Integer.MAX_VALUE, a.getLength());
            assertEquals(Integer.MAX_VALUE, a.getDirectBuffer().capacity());
            assertEquals(Integer.MAX_VALUE, b.getDirectBuffer().limit());
            assertEquals(first, address(a));
            assertEquals(second, address(b));
            assertEquals(before + 2L * Integer.MAX_VALUE, total());
            a.free();
            assertEquals(before + Integer.MAX_VALUE, total());
            b.free();
            memory.verify(() -> MemoryUtil.nmemFree(first));
            memory.verify(() -> MemoryUtil.nmemFree(second));
        }

        assertEquals(before, total());
    }

    // Checks that a failed allocation is retried and the second attempt is used
    @Test
    void retriesAFailedAllocation() {
        AtomicInteger calls = new AtomicInteger();

        try (MockedStatic<MemoryUtil> memory = memory(size -> calls.getAndIncrement() == 0 ? MemoryUtil.NULL : null)) {
            NativeBuffer buffer = new NativeBuffer(8);
            long live = total();

            assertEquals(2, calls.get());
            assertEquals(8, buffer.getLength());
            buffer.getDirectBuffer().putLong(0, -1L);
            assertEquals(-1L, buffer.getDirectBuffer().getLong(0));
            buffer.free();
            assertEquals(live - 8, total());
        }
    }

    // Checks that three failed attempts raise an OutOfMemoryError that reports three attempts and adds nothing to the total
    @Test
    void givesUpAfterThreeAttempts() {
        AtomicInteger calls = new AtomicInteger();
        long before = total();

        try (MockedStatic<MemoryUtil> memory = memory(size -> {
            calls.incrementAndGet();
            return MemoryUtil.NULL;
        })) {
            OutOfMemoryError error = assertThrows(OutOfMemoryError.class, () -> new NativeBuffer(8));

            assertEquals("Couldn't allocate 8 bytes after 3 attempts", error.getMessage());
            assertEquals(3, calls.get());
        }

        assertTrue(total() <= before);
    }

    // Checks that the allocation site is recorded only while tracing is on and starts at the constructor
    @Test
    void recordsTheAllocationSiteOnlyWhenTracing() throws ReflectiveOperationException {
        NativeBuffer untraced = new NativeBuffer(4);
        NativeBuffer.ENABLE_MEMORY_TRACING = true;
        NativeBuffer traced = new NativeBuffer(4);
        StackTraceElement[] frames = ((Throwable) trackerField(tracker(traced), "allocationSite")).getStackTrace();

        assertNull(trackerField(tracker(untraced), "allocationSite"));
        assertEquals(NativeBuffer.class.getName(), frames[0].getClassName());
        assertEquals("<init>", frames[0].getMethodName());
        assertEquals(NativeBufferTest.class.getName(), frames[1].getClassName());
        assertEquals("recordsTheAllocationSiteOnlyWhenTracing", frames[1].getMethodName());
        untraced.free();
        traced.free();
    }

    // Checks that the leak report joins the frames of the allocation site with line breaks
    @Test
    void describesTheAllocationSite() {
        Throwable site = new Throwable();
        StackTraceElement top = new StackTraceElement("a.Top", "run", "Top.java", 3);
        StackTraceElement bottom = new StackTraceElement("b.Bottom", "main", null, -1);

        site.setStackTrace(new StackTraceElement[] {top, bottom});
        assertEquals(top + "\n" + bottom, Statics.call(NativeBuffer.class, "describe", site));
        site.setStackTrace(new StackTraceElement[] {top});
        assertEquals(top.toString(), Statics.call(NativeBuffer.class, "describe", site));
        site.setStackTrace(new StackTraceElement[0]);
        assertEquals("", Statics.call(NativeBuffer.class, "describe", site));
    }

    // Checks that a view covers the whole buffer in native byte order with a fresh position and limit
    @Test
    void viewsCoverTheWholeBuffer() {
        for (int capacity : CAPACITIES) {
            NativeBuffer buffer = new NativeBuffer(capacity);
            ByteBuffer view = buffer.getDirectBuffer();

            assertTrue(view.isDirect());
            assertFalse(view.isReadOnly());
            assertEquals(0, view.position());
            assertEquals(capacity, view.limit());
            assertEquals(capacity, view.capacity());
            assertEquals(ByteOrder.nativeOrder(), view.order());
            buffer.free();
        }
    }

    // Checks that repeated calls return independent views over the same memory
    @Test
    void repeatedCallsReturnIndependentViews() {
        NativeBuffer buffer = new NativeBuffer(16);
        ByteBuffer first = buffer.getDirectBuffer();
        ByteBuffer moved = buffer.getDirectBuffer();
        long address = MemoryUtil.memAddress(first);

        moved.position(5).limit(9).order(ByteOrder.BIG_ENDIAN);

        for (int i = 0; i < 100; i++) {
            ByteBuffer view = buffer.getDirectBuffer();

            assertNotSame(first, view);
            assertNotSame(moved, view);
            assertEquals(0, view.position());
            assertEquals(16, view.limit());
            assertEquals(ByteOrder.nativeOrder(), view.order());
            assertEquals(address, MemoryUtil.memAddress(view));
            view.put(i & 15, (byte) i);
            assertEquals((byte) i, first.get(i & 15));
        }

        assertEquals(5, moved.position());
        assertEquals(9, moved.limit());
        assertEquals(ByteOrder.BIG_ENDIAN, moved.order());
        buffer.free();
    }

    // Checks the first and last index and the index past the end and a negative index and misaligned wide access
    @Test
    void enforcesBoundaryIndexes() {
        NativeBuffer single = new NativeBuffer(1);
        ByteBuffer one = single.getDirectBuffer();

        one.put(0, (byte) 0x80);
        assertEquals((byte) 0x80, one.get(0));
        assertThrows(IndexOutOfBoundsException.class, () -> one.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> one.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> one.getShort(0));
        single.free();

        NativeBuffer nine = new NativeBuffer(9);
        ByteBuffer wide = nine.getDirectBuffer();

        wide.putLong(1, 0x0102030405060708L);
        assertEquals(0x0102030405060708L, wide.getLong(1));
        wide.putInt(5, 0x11223344);
        assertEquals(0x11223344, wide.getInt(5));
        assertThrows(IndexOutOfBoundsException.class, () -> wide.getLong(2));
        assertThrows(IndexOutOfBoundsException.class, () -> wide.getInt(6));
        assertThrows(IndexOutOfBoundsException.class, () -> wide.put(9, (byte) 0));
        nine.free();
    }

    // Checks that live buffers occupy disjoint memory and keep their own contents
    @Test
    void liveBuffersDoNotOverlap() {
        List<NativeBuffer> buffers = new ArrayList<>();

        for (int i = 0; i < CAPACITIES.length; i++) {
            NativeBuffer buffer = new NativeBuffer(CAPACITIES[i]);
            ByteBuffer view = buffer.getDirectBuffer();

            for (int j = 0; j < view.capacity(); j++) {
                view.put(j, (byte) i);
            }

            buffers.add(buffer);
        }

        for (int i = 0; i < buffers.size(); i++) {
            ByteBuffer view = buffers.get(i).getDirectBuffer();

            for (int j = 0; j < view.capacity(); j++) {
                assertEquals((byte) i, view.get(j));
            }
        }

        List<NativeBuffer> sorted = new ArrayList<>(buffers);
        sorted.sort(Comparator.comparingLong(NativeBufferTest::address));

        for (int i = 1; i < sorted.size(); i++) {
            NativeBuffer below = sorted.get(i - 1);
            assertTrue(address(below) + below.getLength() <= address(sorted.get(i)));
        }

        buffers.forEach(NativeBuffer::free);
    }

    // Checks that free releases the memory once and then refuses a second free and new views but keeps the length
    @Test
    void freeReleasesTheMemoryOnce() {
        long before = total();

        try (MockedStatic<MemoryUtil> memory = memory()) {
            NativeBuffer buffer = new NativeBuffer(24);
            long address = address(buffer);

            buffer.free();
            memory.verify(() -> MemoryUtil.nmemFree(address));
            assertEquals(before, total());

            IllegalStateException again = assertThrows(IllegalStateException.class, buffer::free);
            assertEquals("Buffer has been deleted", again.getMessage());
            assertThrows(IllegalStateException.class, buffer::getDirectBuffer);
            assertThrows(IllegalStateException.class, buffer::free);
            assertEquals(24, buffer.getLength());
            memory.verify(() -> MemoryUtil.nmemFree(Mockito.anyLong()));
        }

        assertEquals(before, total());
    }

    // Checks that buffers freed in any order leave the total exact after each free
    @Test
    void freesInAnyOrder() {
        long before = total();
        NativeBuffer[] buffers = new NativeBuffer[6];
        long sum = 0;

        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = new NativeBuffer((i + 1) * 10);
            sum += (i + 1) * 10;
        }

        assertEquals(before + sum, total());

        for (int i : new int[] {2, 5, 0, 3, 1, 4}) {
            buffers[i].free();
            sum -= buffers[i].getLength();
            assertEquals(before + sum, total());
        }

        assertEquals(before, total());
    }

    // Checks that two threads freeing the same buffer release it once and the other call is refused
    @Test
    void racingFreesReleaseOnce() throws Exception {
        int rounds = 5_000;
        NativeBuffer[] buffers = new NativeBuffer[rounds];
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger refused = new AtomicInteger();
        long before = total();

        for (int i = 0; i < rounds; i++) {
            buffers[i] = new NativeBuffer(16);
        }

        Callable<Integer> freer = () -> {
            int freed = 0;

            for (NativeBuffer buffer : buffers) {
                barrier.await();

                try {
                    buffer.free();
                    freed++;
                } catch (IllegalStateException e) {
                    refused.incrementAndGet();
                }
            }

            return freed;
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> first = pool.submit(freer);
            Future<Integer> second = pool.submit(freer);

            assertEquals(rounds, first.get() + second.get());
        } finally {
            pool.shutdownNow();
        }

        assertEquals(rounds, refused.get());
        assertEquals(before, total());
    }

    // Checks that free and reclaim racing on the same buffers release each address exactly once
    @Test
    void freeAndReclaimRacingReleaseOnce() throws Exception {
        int rounds = 5_000;
        NativeBuffer[] buffers = new NativeBuffer[rounds];
        Reference<?>[] trackers = new Reference<?>[rounds];
        List<Long> addresses = new ArrayList<>();
        Queue<Long> freed = new ConcurrentLinkedQueue<>();
        CyclicBarrier barrier = new CyclicBarrier(2);

        for (int i = 0; i < rounds; i++) {
            buffers[i] = new NativeBuffer(8);
            trackers[i] = (Reference<?>) tracker(buffers[i]);
            addresses.add(address(buffers[i]));
        }

        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<?> freer = pool.submit(() -> {
                try (MockedStatic<MemoryUtil> memory = recordingFrees(freed)) {
                    for (NativeBuffer buffer : buffers) {
                        barrier.await();

                        try {
                            buffer.free();
                        } catch (IllegalStateException e) {
                            assertThrows(IllegalStateException.class, buffer::getDirectBuffer);
                        }
                    }
                }

                return null;
            });
            Future<?> reclaimer = pool.submit(() -> {
                try (MockedStatic<MemoryUtil> memory = recordingFrees(freed)) {
                    for (Reference<?> tracker : trackers) {
                        barrier.await();
                        tracker.enqueue();
                        NativeBuffer.reclaim(false);
                    }
                }

                return null;
            });

            freer.get();
            reclaimer.get();
        } finally {
            pool.shutdownNow();
        }

        Map<Long, Long> counts = freed.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        for (int i = 0; i < rounds; i++) {
            assertTrue(released(trackers[i]));
            assertEquals(1L, counts.get(addresses.get(i)), "address of buffer " + i);
        }
    }

    // Checks that concurrent allocation and freeing on several threads keep the total exact
    @Test
    void concurrentAllocationKeepsTheTotalExact() throws Exception {
        int threads = 4;
        int rounds = 20_000;
        CyclicBarrier start = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> work = new ArrayList<>();
        long before = total();

        try {
            for (int t = 0; t < threads; t++) {
                work.add(pool.submit(() -> {
                    NativeBuffer[] held = new NativeBuffer[8];
                    start.await();

                    for (int r = 0; r < rounds; r++) {
                        int slot = r & 7;

                        if (held[slot] != null) {
                            held[slot].free();
                        }

                        held[slot] = new NativeBuffer(r % 64 + 1);
                    }

                    for (NativeBuffer buffer : held) {
                        buffer.free();
                    }

                    return null;
                }));
            }

            for (Future<?> future : work) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(before, total());
    }

    // Checks that a null source is rejected before any allocation
    @Test
    void copyRejectsNull() {
        long before = total();

        try (MockedStatic<MemoryUtil> memory = memory()) {
            assertThrows(NullPointerException.class, () -> NativeBuffer.copy(null));
            memory.verify(() -> MemoryUtil.nmemAlloc(Mockito.anyLong()), Mockito.never());
        }

        assertEquals(before, total());
    }

    // Checks that a failed allocation in copy raises the error and leaves the source unchanged
    @Test
    void copyPropagatesAnAllocationFailure() {
        ByteBuffer src = pattern(ByteBuffer.allocateDirect(8)).position(2);
        long before = total();

        try (MockedStatic<MemoryUtil> memory = memory(size -> MemoryUtil.NULL)) {
            assertThrows(OutOfMemoryError.class, () -> NativeBuffer.copy(src));
            memory.verify(() -> MemoryUtil.nmemAlloc(6L), Mockito.times(3));
        }

        assertEquals(2, src.position());
        assertEquals(8, src.limit());
        assertTrue(total() <= before);
    }

    // Checks that only the remaining bytes of a direct source are copied and its position and limit and mark stay put
    @Test
    void copiesTheRemainingBytesOfADirectSource() {
        ByteBuffer src = pattern(ByteBuffer.allocateDirect(16));

        src.position(3).mark();
        src.position(5).limit(13);
        assertCopies(src);
        src.reset();
        assertEquals(3, src.position());
    }

    // Checks copies from heap sources with an array offset and from read only heap sources
    @Test
    void copiesHeapSources() {
        ByteBuffer whole = pattern(ByteBuffer.allocate(32));

        assertCopies(whole);
        assertCopies(ByteBuffer.wrap(whole.array(), 3, 9));
        assertCopies(whole.slice(7, 20).position(2));
        assertCopies(ByteBuffer.wrap(whole.array(), 1, 30).asReadOnlyBuffer());
        assertCopies(whole.slice(5, 11).asReadOnlyBuffer().position(1).limit(10));
    }

    // Checks copies from read only and big endian direct sources at odd addresses
    @Test
    void copiesDirectSourcesAtOddAddresses() {
        ByteBuffer base = pattern(ByteBuffer.allocateDirect(40));
        ByteBuffer odd = base.slice(1, 39);

        assertEquals(1, MemoryUtil.memAddress(odd) & 1);
        assertCopies(odd);
        assertCopies(odd.asReadOnlyBuffer().position(2));
        assertCopies(base.duplicate().order(ByteOrder.BIG_ENDIAN).position(3).limit(37));
        assertCopies(base.slice(7, 9).asReadOnlyBuffer());
    }

    // Checks that an empty source gives an empty buffer that adds nothing to the total
    @Test
    void copiesAnEmptySource() {
        assertCopies(ByteBuffer.allocateDirect(4).position(4));
        assertCopies(ByteBuffer.allocateDirect(0));
        assertCopies(ByteBuffer.allocate(0));
        assertCopies(ByteBuffer.allocate(6).position(6).asReadOnlyBuffer());
    }

    // Checks that a single byte source is copied from direct and heap memory
    @Test
    void copiesASingleByte() {
        assertCopies(ByteBuffer.allocateDirect(1).put(0, (byte) 0x80));
        assertCopies(ByteBuffer.allocate(1).put(0, (byte) 0x7F));
        assertCopies(pattern(ByteBuffer.allocateDirect(8)).position(7));
    }

    // Checks that repeated copies of one source are independent of each other and of the source
    @Test
    void repeatedCopiesAreIndependent() {
        ByteBuffer src = pattern(ByteBuffer.allocateDirect(12));
        NativeBuffer first = NativeBuffer.copy(src);
        NativeBuffer second = NativeBuffer.copy(src);
        byte original = src.get(0);

        assertNotEquals(address(first), address(second));
        first.getDirectBuffer().put(0, (byte) (original + 1));
        assertEquals(original, second.getDirectBuffer().get(0));
        assertEquals(original, src.get(0));
        src.put(1, (byte) 99);
        assertNotEquals((byte) 99, first.getDirectBuffer().get(1));
        assertNotEquals((byte) 99, second.getDirectBuffer().get(1));
        first.free();
        second.free();
    }

    // Checks that a source from a closed arena is refused and the new buffer is freed again
    @Test
    void copyFreesTheBufferWhenTheSourceIsClosed() {
        Arena arena = Arena.ofShared();
        ByteBuffer src = arena.allocate(16).asByteBuffer();
        long before = total();

        arena.close();

        try (MockedStatic<MemoryUtil> memory = memory()) {
            assertThrows(IllegalStateException.class, () -> NativeBuffer.copy(src));
            memory.verify(() -> MemoryUtil.nmemAlloc(16L));
            memory.verify(() -> MemoryUtil.nmemFree(Mockito.anyLong()));
        }

        assertEquals(before, total());
    }

    // Checks that a source confined to another thread is refused and the new buffer is freed again
    @Test
    void copyFreesTheBufferWhenTheSourceBelongsToAnotherThread() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try (Arena arena = Arena.ofConfined()) {
            ByteBuffer src = arena.allocate(8).asByteBuffer();
            long before = total();
            Future<Throwable> attempt = pool.submit(() -> {
                try {
                    NativeBuffer.copy(src).free();
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            });

            assertInstanceOf(WrongThreadException.class, attempt.get());
            assertEquals(before, total());
        } finally {
            pool.shutdownNow();
        }
    }

    // Checks that reclaim leaves a reachable buffer usable through repeated forced collections
    @Test
    void reclaimKeepsReachableBuffers() throws ReflectiveOperationException {
        NativeBuffer buffer = new NativeBuffer(32);

        buffer.getDirectBuffer().putLong(8, 42L);

        for (int i = 0; i < 3; i++) {
            NativeBuffer.reclaim(true);
            NativeBuffer.reclaim(false);
        }

        assertFalse(released(tracker(buffer)));
        assertEquals(42L, buffer.getDirectBuffer().getLong(8));
        buffer.free();
    }

    // Checks that reclaim frees an enqueued buffer once with and without tracing
    @Test
    void reclaimFreesAnEnqueuedBuffer() throws ReflectiveOperationException {
        for (boolean tracing : new boolean[] {false, true}) {
            NativeBuffer.ENABLE_MEMORY_TRACING = tracing;
            NativeBuffer buffer = new NativeBuffer(40);
            long address = address(buffer);
            Object tracker = tracker(buffer);

            try (MockedStatic<MemoryUtil> memory = memory()) {
                assertTrue(((Reference<?>) tracker).enqueue());
                NativeBuffer.reclaim(false);
                memory.verify(() -> MemoryUtil.nmemFree(address));
            }

            assertTrue(released(tracker));
            assertThrows(IllegalStateException.class, buffer::getDirectBuffer);
            assertThrows(IllegalStateException.class, buffer::free);
        }
    }

    // Checks that reclaim skips a buffer that was freed before its tracker was enqueued
    @Test
    void reclaimSkipsAFreedBuffer() throws ReflectiveOperationException {
        NativeBuffer buffer = new NativeBuffer(12);
        long address = address(buffer);
        Object tracker = tracker(buffer);

        try (MockedStatic<MemoryUtil> memory = memory()) {
            buffer.free();
            assertTrue(((Reference<?>) tracker).enqueue());
            NativeBuffer.reclaim(false);
            NativeBuffer.reclaim(false);
            memory.verify(() -> MemoryUtil.nmemFree(address));
        }
    }

    // Checks that buffers leaked with and without tracing are freed by reclaim after their owners are collected
    @Test
    void reclaimFreesLeakedBuffersAfterCollection() throws Exception {
        NativeBuffer.ENABLE_MEMORY_TRACING = true;
        Object traced = leak(16);
        NativeBuffer.ENABLE_MEMORY_TRACING = false;
        Object untraced = leak(32);

        for (int i = 0; i < 200 && !(released(traced) && released(untraced)); i++) {
            NativeBuffer.reclaim(true);
            Thread.sleep(10);
        }

        assertTrue(released(traced));
        assertTrue(released(untraced));
    }
}
