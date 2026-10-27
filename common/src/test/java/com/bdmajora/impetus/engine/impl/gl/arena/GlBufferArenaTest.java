package com.bdmajora.impetus.engine.impl.gl.arena;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.arena.staging.FallbackStagingBuffer;
import com.bdmajora.impetus.engine.impl.gl.arena.staging.StagingBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferUsage;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.testing.Devices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.system.MemoryUtil;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;

// Tests of the buffer arena against a command list mock whose buffers hold real bytes
class GlBufferArenaTest {
    // Usage hint of every arena buffer
    private static final GlBufferUsage USAGE = GlBufferUsage.STATIC_DRAW;
    // Handle of the private segment list check of the arena
    private static final MethodHandle CHECK;
    // Handle of the first segment field of the arena
    private static final VarHandle HEAD;
    // Handle of the live element count field of the arena
    private static final VarHandle USED;
    // Handle of the free index field of the arena
    private static final VarHandle FREE_SEGMENTS;

    // Resolves the private members of the arena
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(GlBufferArena.class, MethodHandles.lookup());
            CHECK = lookup.findVirtual(GlBufferArena.class, "checkAssertions0", MethodType.methodType(void.class));
            HEAD = lookup.findVarHandle(GlBufferArena.class, "head", GlBufferSegment.class);
            USED = lookup.findVarHandle(GlBufferArena.class, "used", int.class);
            FREE_SEGMENTS = lookup.findVarHandle(GlBufferArena.class, "freeSegments", TreeSet.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Buffers the command list mock created and has not deleted
    private final Set<GlBuffer> live = Collections.newSetFromMap(new IdentityHashMap<>());
    // Storage size in bytes of every live buffer with storage
    private final Map<GlBuffer, Long> sizes = new IdentityHashMap<>();
    // Bytes of every live buffer with storage while bytes are modelled
    private final Map<GlBuffer, byte[]> bytes = new IdentityHashMap<>();
    // Copies staged after the last flush
    private final List<Runnable> staged = new ArrayList<>();
    // Pattern written into the data of each upload
    private final Map<PendingUpload, byte[]> expected = new IdentityHashMap<>();
    // Native buffers freed after each test
    private final List<NativeBuffer> natives = new ArrayList<>();
    // Raw native allocations freed after each test
    private final List<Long> addresses = new ArrayList<>();
    // Answer that creates and records a buffer
    private final Answer<GlMutableBuffer> createAnswer = invocation -> create();
    // Answer that gives a buffer storage
    private final Answer<Void> allocateAnswer = invocation -> {
        allocate(invocation.getArgument(0), invocation.getArgument(1));
        return null;
    };
    // Answer that copies a range between two buffers
    private final Answer<Void> copyAnswer = invocation -> {
        copy(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(4));
        return null;
    };
    // Answer that deletes a buffer
    private final Answer<Void> deleteAnswer = invocation -> {
        release(invocation.getArgument(0));
        return null;
    };
    // Answer that stages a copy for the next flush
    private final Answer<Void> stageAnswer = invocation -> {
        stage(invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3));
        return null;
    };
    // Answer that applies every staged copy
    private final Answer<Void> flushAnswer = invocation -> {
        flushStaged();
        return null;
    };
    // Switch that makes the mock keep the bytes of every buffer
    private boolean modelBytes = true;
    // Element size of the arena under test
    private int stride;
    // Command list mock
    private CommandList commands;
    // Staging buffer mock
    private StagingBuffer staging;

    // Creates the command list and staging buffer mocks
    @BeforeEach
    void createMocks() {
        commands = Mockito.mock(CommandList.class);
        Mockito.when(commands.createMutableBuffer()).thenAnswer(createAnswer);
        Mockito.doAnswer(allocateAnswer).when(commands).allocateStorage(any(), anyLong(), any());
        Mockito.doAnswer(copyAnswer).when(commands).copyBufferSubData(any(), any(), anyLong(), anyLong(), anyLong());
        Mockito.doAnswer(deleteAnswer).when(commands).deleteBuffer(any());
        staging = Mockito.mock(StagingBuffer.class);
        Mockito.doAnswer(stageAnswer).when(staging).enqueueCopy(any(), any(), any(), anyLong());
        Mockito.doAnswer(flushAnswer).when(staging).flush(any());
    }

    // Frees the native memory of the test
    @AfterEach
    void freeNatives() {
        for (NativeBuffer buffer : natives) {
            buffer.free();
        }
        for (long address : addresses) {
            MemoryUtil.nmemFree(address);
        }
        natives.clear();
        addresses.clear();
    }

    // Records a new buffer as live
    private GlMutableBuffer create() {
        GlMutableBuffer buffer = new GlMutableBuffer();
        live.add(buffer);
        return buffer;
    }

    // Gives a live buffer storage of the given size
    private void allocate(GlBuffer buffer, long size) {
        assertTrue(live.contains(buffer), "Storage for a buffer that is not live");
        sizes.put(buffer, size);
        if (modelBytes) {
            bytes.put(buffer, new byte[Math.toIntExact(size)]);
        }
    }

    // Copies a range between two live buffers and rejects an empty range or one outside either buffer
    private void copy(GlBuffer src, GlBuffer dst, long read, long write, long length) {
        assertTrue(length > 0, "Empty copy");
        assertRange(src, read, length);
        assertRange(dst, write, length);
        if (modelBytes) {
            System.arraycopy(bytes.get(src), (int) read, bytes.get(dst), (int) write, (int) length);
        }
    }

    // Asserts that the range lies inside the storage of a live buffer
    private void assertRange(GlBuffer buffer, long offset, long length) {
        assertTrue(live.contains(buffer), "Access to a buffer that is not live");
        assertTrue(offset >= 0 && offset + length <= sizes.get(buffer), "Range outside the buffer");
    }

    // Deletes a live buffer and fails on a buffer that is not live
    private void release(GlBuffer buffer) {
        assertTrue(live.remove(buffer), "Delete of a buffer that is not live");
        sizes.remove(buffer);
        bytes.remove(buffer);
    }

    // Stages a copy of the remaining data into the destination for the next flush
    private void stage(ByteBuffer data, GlBuffer dst, long write) {
        int length = data.remaining();
        assertRange(dst, write, length);
        byte[] copy = modelBytes ? new byte[length] : null;
        if (copy != null) {
            data.duplicate().get(copy);
        }
        staged.add(() -> {
            assertTrue(live.contains(dst), "Staged copy flushed into a buffer that is not live");
            if (copy != null) {
                System.arraycopy(copy, 0, bytes.get(dst), (int) write, length);
            }
        });
    }

    // Applies every staged copy in order
    private void flushStaged() {
        for (Runnable copy : staged) {
            copy.run();
        }
        staged.clear();
    }

    // Creates the arena under test and clears the construction calls from the mocks
    private GlBufferArena arena(int stride, int minimumBytes) {
        this.stride = stride;
        GlBufferArena arena = new GlBufferArena(commands, stride, minimumBytes, staging);
        Mockito.clearInvocations(commands, staging);
        return arena;
    }

    // Creates an upload of the given number of bytes filled with a pattern unique to the upload
    private PendingUpload upload(int length) {
        NativeBuffer data = new NativeBuffer(length);
        natives.add(data);
        byte[] pattern = new byte[length];
        int seed = natives.size();
        for (int i = 0; i < length; i++) {
            pattern[i] = (byte) (seed * 131 + i * 7 + 1);
        }
        data.getDirectBuffer().put(0, pattern);
        PendingUpload upload = PendingUpload.of(data);
        expected.put(upload, pattern);
        return upload;
    }

    // Creates an upload of the given number of whole elements
    private PendingUpload elements(int count) {
        return upload(count * stride);
    }

    // Creates an upload whose data claims the given length over one byte of native memory
    private PendingUpload huge(int length) {
        long address = MemoryUtil.nmemAlloc(1);
        addresses.add(address);
        NativeBuffer data = Mockito.mock(NativeBuffer.class);
        Mockito.when(data.getLength()).thenReturn(length);
        Mockito.when(data.getDirectBuffer()).thenAnswer(invocation -> MemoryUtil.memByteBuffer(address, length));
        return PendingUpload.of(data);
    }

    // Returns a mutable queue of the uploads
    private static List<PendingUpload> queue(PendingUpload... uploads) {
        return new ArrayList<>(Arrays.asList(uploads));
    }

    // Uploads every upload in one batch and asserts that the queue was consumed and returns whether the buffer changed
    private boolean place(GlBufferArena arena, PendingUpload... uploads) {
        List<PendingUpload> queue = queue(uploads);
        boolean changed = arena.upload(commands, queue);
        assertTrue(queue.isEmpty());
        return changed;
    }

    // Returns the element offsets of the results of the uploads
    private static List<Integer> offsets(PendingUpload... uploads) {
        List<Integer> offsets = new ArrayList<>();
        for (PendingUpload upload : uploads) {
            offsets.add(upload.getResult().getOffset());
        }
        return offsets;
    }

    // Returns the capacity of the arena in elements
    private long capacity(GlBufferArena arena) {
        return arena.getDeviceAllocatedMemoryL() / stride;
    }

    // Returns the live elements of the arena
    private long used(GlBufferArena arena) {
        return arena.getDeviceUsedMemoryL() / stride;
    }

    // Asserts that the arena buffer holds the data of each placed upload
    private void assertHolds(GlBufferArena arena, Iterable<PendingUpload> uploads) {
        byte[] storage = bytes.get(arena.getBufferObject());
        for (PendingUpload upload : uploads) {
            byte[] data = expected.get(upload);
            int start = upload.getResult().getOffset() * stride;
            assertArrayEquals(data, Arrays.copyOfRange(storage, start, start + data.length));
        }
    }

    // Asserts that the arena buffer holds the data of each placed upload
    private void assertHolds(GlBufferArena arena, PendingUpload... uploads) {
        assertHolds(arena, Arrays.asList(uploads));
    }

    // Runs the segment list check of the arena
    private static void check(GlBufferArena arena) {
        try {
            CHECK.invoke(arena);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new AssertionError(t);
        }
    }

    // Fills a fresh arena of sixteen elements with one element uploads and returns them by offset
    private PendingUpload[] singles(GlBufferArena arena) {
        PendingUpload[] byOffset = new PendingUpload[16];
        for (int round = 0; round < 2; round++) {
            PendingUpload[] uploads = new PendingUpload[8];
            for (int i = 0; i < uploads.length; i++) {
                uploads[i] = elements(1);
            }
            place(arena, uploads);
            for (PendingUpload upload : uploads) {
                byOffset[upload.getResult().getOffset()] = upload;
            }
        }
        assertEquals(16, capacity(arena));
        assertEquals(16, used(arena));
        return byOffset;
    }

    // Places uploads of the given element counts and a filler for the rest of the free space and returns them in queue order
    private PendingUpload[] full(GlBufferArena arena, int... counts) {
        PendingUpload[] uploads = new PendingUpload[counts.length + 1];
        for (int i = 0; i < counts.length; i++) {
            uploads[i] = elements(counts[i]);
        }
        place(arena, Arrays.copyOf(uploads, counts.length));
        uploads[counts.length] = elements((int) (capacity(arena) - used(arena)));
        assertFalse(place(arena, uploads[counts.length]));
        assertEquals(capacity(arena), used(arena));
        return uploads;
    }

    // Verifies that the constructor creates one empty buffer and touches nothing else
    @Test
    void constructorCreatesOneZeroByteBuffer() {
        GlBufferArena arena = new GlBufferArena(commands, 4, 64, staging);
        GlBuffer buffer = arena.getBufferObject();
        Mockito.verify(commands).createMutableBuffer();
        Mockito.verify(commands).allocateStorage(same((GlMutableBuffer) buffer), eq(0L), eq(USAGE));
        Mockito.verifyNoMoreInteractions(commands);
        Mockito.verifyNoInteractions(staging);
        assertEquals(Set.of(buffer), live);
        assertEquals(0, arena.getDeviceAllocatedMemoryL());
        assertEquals(0, arena.getDeviceUsedMemoryL());
        assertTrue(arena.isEmpty());
        assertFalse(arena.isDeleted());
        check(arena);
    }

    // Verifies that null arguments are rejected before any buffer exists
    @Test
    void constructorRejectsNullArgumentsBeforeCreatingABuffer() {
        assertThrows(NullPointerException.class, () -> new GlBufferArena(null, 4, 64, staging));
        assertThrows(NullPointerException.class, () -> new GlBufferArena(commands, 4, 64, null));
        Mockito.verifyNoInteractions(commands, staging);
    }

    // Verifies that a stride below one is rejected before any buffer exists
    @ParameterizedTest
    @ValueSource(ints = {0, -1, -4, Integer.MIN_VALUE})
    void constructorRejectsAStrideBelowOne(int stride) {
        assertThrows(IllegalArgumentException.class, () -> new GlBufferArena(commands, stride, 64, staging));
        Mockito.verifyNoInteractions(commands, staging);
    }

    // Verifies that a negative minimum capacity is rejected before any buffer exists
    @ParameterizedTest
    @ValueSource(ints = {-1, -64, Integer.MIN_VALUE})
    void constructorRejectsANegativeMinimumCapacity(int minimumBytes) {
        assertThrows(IllegalArgumentException.class, () -> new GlBufferArena(commands, 4, minimumBytes, staging));
        Mockito.verifyNoInteractions(commands, staging);
    }

    // Verifies that the constructor deletes its buffer when the storage allocation throws
    @Test
    void constructorDeletesItsBufferWhenStorageAllocationThrows() {
        IllegalStateException failure = new IllegalStateException("Out of memory");
        Mockito.doThrow(failure).when(commands).allocateStorage(any(), anyLong(), any());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> new GlBufferArena(commands, 4, 64, staging)));
        Mockito.verify(commands).deleteBuffer(any());
        assertTrue(live.isEmpty());
    }

    // Verifies that the minimum capacity rounds up to whole elements and holds at the int limits
    @ParameterizedTest
    @CsvSource({
            "4, 0, 8",
            "4, 1, 8",
            "4, 64, 64",
            "4, 65, 68",
            "3, 2147483647, 2147483649",
            "1, 2147483647, 2147483647",
            "2147483647, 2147483647, 2147483647",
            "2147483647, 0, 2147483647"
    })
    void minimumCapacityRoundsUpToWholeElements(int stride, int minimumBytes, long expectedBytes) {
        modelBytes = false;
        GlBufferArena arena = arena(stride, minimumBytes);
        arena.ensureCapacity(commands, 1);
        assertEquals(expectedBytes, arena.getDeviceAllocatedMemoryL());
        assertEquals(expectedBytes, sizes.get(arena.getBufferObject()));
    }

    // Verifies that both memory getters multiply in long past the int range
    @Test
    void memoryGettersMultiplyInLong() {
        modelBytes = false;
        GlBufferArena arena = arena(1 << 20, 0);
        arena.ensureCapacity(commands, 4096);
        assertEquals(6144L << 20, arena.getDeviceAllocatedMemoryL());
        PendingUpload first = huge(Integer.MAX_VALUE);
        PendingUpload second = huge(Integer.MAX_VALUE);
        assertFalse(place(arena, first, second));
        assertEquals(List.of(4096, 2048), offsets(first, second));
        assertEquals(4096L << 20, arena.getDeviceUsedMemoryL());
        Mockito.verify(staging).enqueueCopy(same(commands), any(), same(arena.getBufferObject()), eq(4096L << 20));
        Mockito.verify(staging).enqueueCopy(same(commands), any(), same(arena.getBufferObject()), eq(2048L << 20));
    }

    // Verifies that a deleted arena reports no memory and no live elements
    @Test
    void deletedArenaReportsNoMemory() {
        GlBufferArena arena = arena(4, 0);
        place(arena, elements(3), elements(5));
        assertEquals(32, arena.getDeviceUsedMemoryL());
        arena.delete(commands);
        assertEquals(0, arena.getDeviceAllocatedMemoryL());
        assertEquals(0, arena.getDeviceUsedMemoryL());
        assertTrue(arena.isEmpty());
        check(arena);
    }

    // Verifies that an empty queue flushes the staging buffer and makes no GL call
    @Test
    void emptyQueueOnlyFlushes() {
        GlBufferArena arena = arena(4, 64);
        GlBuffer buffer = arena.getBufferObject();
        assertFalse(arena.upload(commands, queue()));
        Mockito.verify(staging).flush(commands);
        Mockito.verifyNoMoreInteractions(staging);
        Mockito.verifyNoInteractions(commands);
        assertSame(buffer, arena.getBufferObject());
    }

    // Verifies that the first upload grows once and carves each segment from the end of the free space
    @Test
    void firstUploadGrowsOnceAndCarvesFromTheEnd() {
        GlBufferArena arena = arena(4, 64);
        GlBuffer empty = arena.getBufferObject();
        PendingUpload a = upload(16);
        PendingUpload b = upload(16);
        PendingUpload c = upload(16);
        assertTrue(place(arena, a, b, c));
        GlMutableBuffer buffer = (GlMutableBuffer) arena.getBufferObject();
        assertNotSame(empty, buffer);
        InOrder order = Mockito.inOrder(commands, staging);
        order.verify(staging).flush(commands);
        order.verify(commands).createMutableBuffer();
        order.verify(commands).allocateStorage(same(buffer), eq(96L), eq(USAGE));
        order.verify(commands).deleteBuffer(same(empty));
        order.verify(staging).enqueueCopy(same(commands), any(), same(buffer), eq(80L));
        order.verify(staging).enqueueCopy(same(commands), any(), same(buffer), eq(64L));
        order.verify(staging).enqueueCopy(same(commands), any(), same(buffer), eq(48L));
        order.verify(staging).flush(commands);
        order.verifyNoMoreInteractions();
        assertEquals(List.of(20, 16, 12), offsets(a, b, c));
        assertEquals(4, a.getResult().getLength());
        assertEquals(96, arena.getDeviceAllocatedMemoryL());
        assertEquals(48, arena.getDeviceUsedMemoryL());
        assertFalse(arena.isEmpty());
        assertHolds(arena, a, b, c);
        check(arena);
    }

    // Verifies that uploads that fit keep the buffer and report no change
    @Test
    void uploadsThatFitKeepTheBuffer() {
        GlBufferArena arena = arena(4, 64);
        PendingUpload a = upload(16);
        place(arena, a);
        GlBuffer buffer = arena.getBufferObject();
        PendingUpload b = upload(16);
        assertFalse(place(arena, b));
        assertSame(buffer, arena.getBufferObject());
        assertEquals(List.of(12, 8), offsets(a, b));
        assertHolds(arena, a, b);
    }

    // Verifies that best fit takes an exact hole over a larger hole at a lower offset
    @Test
    void bestFitPrefersAnExactHole() {
        GlBufferArena arena = arena(4, 0);
        PendingUpload[] layout = full(arena, 3, 1, 5, 1);
        assertEquals(List.of(17, 16, 11, 10, 0), offsets(layout));
        layout[0].getResult().delete();
        layout[2].getResult().delete();
        PendingUpload exact = elements(3);
        assertFalse(place(arena, exact));
        assertEquals(17, exact.getResult().getOffset());
        PendingUpload larger = elements(3);
        assertFalse(place(arena, larger));
        assertEquals(13, larger.getResult().getOffset());
        assertHolds(arena, layout[1], layout[3], layout[4], exact, larger);
        check(arena);
    }

    // Verifies that best fit takes the smallest larger hole and carves its end
    @Test
    void bestFitPicksTheSmallestLargerHole() {
        GlBufferArena arena = arena(4, 0);
        PendingUpload[] layout = full(arena, 6, 1, 4, 1);
        assertEquals(List.of(18, 17, 13, 12, 0), offsets(layout));
        layout[0].getResult().delete();
        layout[2].getResult().delete();
        PendingUpload carved = elements(3);
        assertFalse(place(arena, carved));
        assertEquals(14, carved.getResult().getOffset());
        assertEquals(3, carved.getResult().getLength());
        check(arena);
    }

    // Verifies that best fit breaks a tie between equal holes by the lowest offset
    @Test
    void bestFitBreaksTiesByLowestOffset() {
        GlBufferArena arena = arena(4, 0);
        PendingUpload[] layout = full(arena, 5, 1, 5, 1);
        assertEquals(List.of(19, 18, 13, 12, 0), offsets(layout));
        layout[0].getResult().delete();
        layout[2].getResult().delete();
        PendingUpload low = elements(3);
        PendingUpload exact = elements(5);
        PendingUpload rest = elements(2);
        assertFalse(place(arena, low, exact, rest));
        assertEquals(List.of(15, 19, 13), offsets(low, exact, rest));
        assertEquals(capacity(arena), used(arena));
        assertHolds(arena, layout[1], layout[3], layout[4], low, exact, rest);
        check(arena);
    }

    // Verifies that a request at exactly half the capacity compacts with one copy per isolated segment
    @Test
    void requestAtHalfTheCapacityCompacts() {
        GlBufferArena arena = arena(4, 0);
        PendingUpload[] singles = singles(arena);
        for (int offset = 0; offset < 16; offset++) {
            if (offset % 4 != 3) {
                singles[offset].getResult().delete();
            }
        }
        GlBuffer old = arena.getBufferObject();
        Mockito.clearInvocations(commands);
        PendingUpload request = elements(4);
        assertTrue(place(arena, request));
        GlBuffer buffer = arena.getBufferObject();
        Mockito.verify(commands).copyBufferSubData(same(old), same(buffer), eq(12L), eq(48L), eq(4L));
        Mockito.verify(commands).copyBufferSubData(same(old), same(buffer), eq(28L), eq(52L), eq(4L));
        Mockito.verify(commands).copyBufferSubData(same(old), same(buffer), eq(44L), eq(56L), eq(4L));
        Mockito.verify(commands).copyBufferSubData(same(old), same(buffer), eq(60L), eq(60L), eq(4L));
        Mockito.verify(commands, Mockito.times(4)).copyBufferSubData(any(), any(), anyLong(), anyLong(), anyLong());
        assertEquals(16, capacity(arena));
        assertEquals(List.of(12, 13, 14, 15, 8), offsets(singles[3], singles[7], singles[11], singles[15], request));
        assertHolds(arena, singles[3], singles[7], singles[11], singles[15], request);
        check(arena);
    }

    // Verifies that a request one past half the capacity grows instead of compacting
    @Test
    void requestPastHalfTheCapacityGrows() {
        GlBufferArena arena = arena(4, 0);
        PendingUpload[] singles = singles(arena);
        for (int offset = 0; offset < 16; offset++) {
            if (offset % 4 != 3) {
                singles[offset].getResult().delete();
            }
        }
        PendingUpload request = elements(5);
        assertTrue(place(arena, request));
        assertEquals(32, capacity(arena));
        assertEquals(List.of(28, 29, 30, 31, 23), offsets(singles[3], singles[7], singles[11], singles[15], request));
        assertHolds(arena, singles[3], singles[7], singles[11], singles[15], request);
        check(arena);
    }

    // Verifies that compaction merges each run of adjacent live segments into one copy
    @Test
    void compactionMergesAdjacentLiveSegmentsIntoOneCopy() {
        GlBufferArena arena = arena(4, 0);
        PendingUpload[] singles = singles(arena);
        Set<Integer> kept = Set.of(0, 1, 2, 5, 9, 10);
        for (int offset = 0; offset < 16; offset++) {
            if (!kept.contains(offset)) {
                singles[offset].getResult().delete();
            }
        }
        GlBuffer old = arena.getBufferObject();
        Mockito.clearInvocations(commands);
        PendingUpload request = elements(6);
        assertTrue(place(arena, request));
        GlBuffer buffer = arena.getBufferObject();
        InOrder order = Mockito.inOrder(commands);
        order.verify(commands).copyBufferSubData(same(old), same(buffer), eq(0L), eq(104L), eq(12L));
        order.verify(commands).copyBufferSubData(same(old), same(buffer), eq(20L), eq(116L), eq(4L));
        order.verify(commands).copyBufferSubData(same(old), same(buffer), eq(36L), eq(120L), eq(8L));
        Mockito.verify(commands, Mockito.times(3)).copyBufferSubData(any(), any(), anyLong(), anyLong(), anyLong());
        assertEquals(32, capacity(arena));
        assertEquals(List.of(26, 27, 28, 29, 30, 31, 20), offsets(singles[0], singles[1], singles[2], singles[5], singles[9], singles[10], request));
        assertHolds(arena, singles[0], singles[1], singles[2], singles[5], singles[9], singles[10], request);
        check(arena);
    }

    // Verifies that a zero byte upload into a fresh arena neither grows nor throws
    @Test
    void zeroByteUploadIntoAFreshArenaNeitherGrowsNorThrows() {
        GlBufferArena arena = arena(4, 64);
        GlBuffer buffer = arena.getBufferObject();
        PendingUpload empty = upload(0);
        assertFalse(place(arena, empty));
        assertSame(buffer, arena.getBufferObject());
        Mockito.verify(commands, Mockito.never()).createMutableBuffer();
        Mockito.verify(staging).enqueueCopy(same(commands), any(), same(buffer), eq(0L));
        GlBufferSegment segment = empty.getResult();
        assertEquals(0, segment.getOffset());
        assertEquals(0, segment.getLength());
        assertTrue(arena.isEmpty());
        assertEquals(0, arena.getDeviceAllocatedMemoryL());
        segment.delete();
        assertThrows(IllegalStateException.class, segment::delete);
        check(arena);
    }

    // Verifies that a zero byte upload into a full arena does not grow it
    @Test
    void zeroByteUploadIntoAFullArenaDoesNotGrow() {
        GlBufferArena arena = arena(4, 0);
        PendingUpload[] layout = full(arena, 2, 3);
        GlBuffer buffer = arena.getBufferObject();
        PendingUpload empty = upload(0);
        assertFalse(place(arena, empty));
        assertSame(buffer, arena.getBufferObject());
        assertEquals(capacity(arena), used(arena));
        assertHolds(arena, layout);
        check(arena);
    }

    // Verifies that zero byte segments stay out of the segment list between live segments
    @Test
    void zeroByteSegmentsLeaveNoHoleWhenFreed() {
        GlBufferArena arena = arena(4, 0);
        arena.ensureCapacity(commands, 8);
        PendingUpload a = elements(2);
        PendingUpload empty = upload(0);
        PendingUpload b = elements(2);
        assertFalse(place(arena, a, empty, b));
        assertEquals(List.of(14, 0, 12), offsets(a, empty, b));
        empty.getResult().delete();
        check(arena);
        a.getResult().delete();
        b.getResult().delete();
        PendingUpload whole = elements(16);
        assertFalse(place(arena, whole));
        assertEquals(0, whole.getResult().getOffset());
        check(arena);
    }

    // Verifies that partial elements round up and every write offset stays element aligned
    @ParameterizedTest
    @ValueSource(ints = {2, 3, 20})
    void partialElementsRoundUpAndStayAligned(int stride) {
        GlBufferArena arena = arena(stride, 0);
        int[] lengths = {1, stride - 1, stride, stride + 1, 2 * stride - 1, 2 * stride + 1};
        PendingUpload[] uploads = new PendingUpload[lengths.length];
        for (int i = 0; i < lengths.length; i++) {
            uploads[i] = upload(lengths[i]);
        }
        place(arena, uploads);
        int end = (int) capacity(arena);
        for (int i = 0; i < uploads.length; i++) {
            GlBufferSegment segment = uploads[i].getResult();
            assertEquals(Math.ceilDiv(lengths[i], stride), segment.getLength());
            assertEquals(end - segment.getLength(), segment.getOffset());
            Mockito.verify(staging).enqueueCopy(same(commands), any(), same(arena.getBufferObject()), eq((long) segment.getOffset() * stride));
            end = segment.getOffset();
        }
        assertHolds(arena, uploads);
        check(arena);
    }

    // Verifies that a null command list or queue is rejected before any work
    @Test
    void uploadRejectsNullArgumentsBeforeAnyWork() {
        GlBufferArena arena = arena(4, 64);
        assertThrows(NullPointerException.class, () -> arena.upload(null, queue()));
        List<PendingUpload> pending = queue(upload(4));
        assertThrows(NullPointerException.class, () -> arena.upload(null, pending));
        assertThrows(NullPointerException.class, () -> arena.upload(commands, null));
        assertEquals(1, pending.size());
        Mockito.verifyNoInteractions(commands, staging);
        check(arena);
    }

    // Verifies that a null element leaves placed uploads out of the queue and the rest in it
    @Test
    void nullElementKeepsTheQueueConsistent() {
        GlBufferArena arena = arena(4, 0);
        arena.ensureCapacity(commands, 8);
        PendingUpload first = elements(2);
        PendingUpload last = elements(2);
        List<PendingUpload> pending = queue(first, null, last);
        assertThrows(NullPointerException.class, () -> arena.upload(commands, pending));
        assertEquals(Arrays.asList(null, last), pending);
        assertEquals(14, first.getResult().getOffset());
        assertThrows(IllegalStateException.class, last::getResult);
        assertEquals(2, used(arena));
        check(arena);
    }

    // Verifies that a freed native buffer is rejected before any allocation or growth
    @Test
    void freedNativeBufferIsRejectedBeforeAnyAllocation() {
        GlBufferArena arena = arena(4, 64);
        NativeBuffer data = new NativeBuffer(8);
        data.free();
        List<PendingUpload> pending = queue(PendingUpload.of(data));
        assertThrows(IllegalStateException.class, () -> arena.upload(commands, pending));
        assertEquals(1, pending.size());
        Mockito.verifyNoInteractions(commands, staging);
        assertTrue(arena.isEmpty());
        check(arena);
    }

    // Verifies that the second copy of one upload in a queue releases its segment
    @Test
    void duplicateUploadInOneQueueReleasesTheSecondSegment() {
        GlBufferArena arena = arena(4, 0);
        arena.ensureCapacity(commands, 8);
        PendingUpload twice = elements(2);
        List<PendingUpload> pending = queue(twice, twice);
        assertThrows(IllegalStateException.class, () -> arena.upload(commands, pending));
        assertEquals(List.of(twice), pending);
        assertEquals(14, twice.getResult().getOffset());
        assertEquals(2, used(arena));
        check(arena);
        flushStaged();
        assertHolds(arena, twice);
    }

    // Verifies that a placed upload cannot be placed again and leaks nothing
    @Test
    void placedUploadIsRejectedWithoutALeak() {
        GlBufferArena arena = arena(4, 0);
        PendingUpload placed = elements(2);
        place(arena, placed);
        GlBufferSegment segment = placed.getResult();
        List<PendingUpload> pending = queue(placed);
        assertThrows(IllegalStateException.class, () -> arena.upload(commands, pending));
        assertEquals(List.of(placed), pending);
        assertSame(segment, placed.getResult());
        assertEquals(2, used(arena));
        check(arena);
    }

    // Verifies that a staging failure releases the segment and keeps the upload queued for a retry
    @Test
    void stagingFailureReleasesTheSegment() {
        GlBufferArena arena = arena(4, 0);
        arena.ensureCapacity(commands, 8);
        IllegalStateException failure = new IllegalStateException("Staging failed");
        Mockito.doThrow(failure).doAnswer(stageAnswer).when(staging).enqueueCopy(any(), any(), any(), anyLong());
        PendingUpload a = elements(2);
        PendingUpload b = elements(2);
        List<PendingUpload> pending = queue(a, b);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> arena.upload(commands, pending)));
        assertEquals(List.of(a, b), pending);
        assertThrows(IllegalStateException.class, a::getResult);
        assertTrue(arena.isEmpty());
        check(arena);
        assertFalse(arena.upload(commands, pending));
        assertTrue(pending.isEmpty());
        assertEquals(List.of(14, 12), offsets(a, b));
        assertHolds(arena, a, b);
    }

    // Verifies that a flush failure propagates after the placed uploads left the queue
    @Test
    void flushFailurePropagatesAfterThePlacedUploadsLeft() {
        GlBufferArena arena = arena(4, 0);
        arena.ensureCapacity(commands, 8);
        IllegalStateException failure = new IllegalStateException("Flush failed");
        Mockito.doThrow(failure).when(staging).flush(any());
        PendingUpload a = elements(2);
        List<PendingUpload> pending = queue(a);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> arena.upload(commands, pending)));
        assertTrue(pending.isEmpty());
        assertEquals(14, a.getResult().getOffset());
        check(arena);
    }

    // Verifies that an upload into a deleted arena is rejected without any call
    @Test
    void uploadAfterDeleteIsRejected() {
        GlBufferArena arena = arena(4, 64);
        arena.delete(commands);
        Mockito.clearInvocations(commands);
        List<PendingUpload> pending = queue(upload(16));
        assertThrows(IllegalStateException.class, () -> arena.upload(commands, pending));
        assertThrows(IllegalStateException.class, () -> arena.upload(commands, queue()));
        assertEquals(1, pending.size());
        Mockito.verifyNoInteractions(commands, staging);
    }

    // Verifies that uploads that do not fit keep their order through growth in any mutable list
    @Test
    void uploadsThatDoNotFitKeepTheirOrderInAnyMutableList() {
        List<Supplier<List<PendingUpload>>> kinds = List.of(ArrayList::new, LinkedList::new);
        for (Supplier<List<PendingUpload>> kind : kinds) {
            GlBufferArena arena = arena(4, 0);
            arena.ensureCapacity(commands, 8);
            PendingUpload big = elements(20);
            PendingUpload small = elements(2);
            PendingUpload bigger = elements(30);
            List<PendingUpload> pending = kind.get();
            pending.addAll(List.of(big, small, bigger));
            assertTrue(arena.upload(commands, pending));
            assertTrue(pending.isEmpty());
            assertEquals(104, capacity(arena));
            assertEquals(List.of(82, 102, 52), offsets(big, small, bigger));
            assertHolds(arena, big, small, bigger);
            check(arena);
        }
    }

    // Verifies that an immutable queue that cannot be consumed is rejected
    @Test
    void immutableQueueIsRejected() {
        GlBufferArena arena = arena(4, 64);
        assertThrows(UnsupportedOperationException.class, () -> arena.upload(commands, List.of(upload(16))));
        assertTrue(arena.isEmpty());
        Mockito.verify(commands, Mockito.never()).createMutableBuffer();
    }

    // Verifies that remaining uploads past the int range throw before any allocation
    @Test
    void remainingElementsPastTheIntRangeThrowOutOfMemory() {
        modelBytes = false;
        GlBufferArena arena = arena(1, 0);
        List<PendingUpload> pending = queue(huge(Integer.MAX_VALUE), huge(Integer.MAX_VALUE));
        assertThrows(OutOfMemoryError.class, () -> arena.upload(commands, pending));
        assertEquals(2, pending.size());
        Mockito.verify(commands, Mockito.never()).createMutableBuffer();
        Mockito.verify(staging, Mockito.never()).enqueueCopy(any(), any(), any(), anyLong());
        assertTrue(arena.isEmpty());
        check(arena);
    }

    // Verifies that an upload of the int limit fits exactly and one more element throws
    @Test
    void uploadOfTheIntLimitFitsExactly() {
        modelBytes = false;
        GlBufferArena arena = arena(1, 0);
        PendingUpload limit = huge(Integer.MAX_VALUE);
        assertTrue(place(arena, limit));
        assertEquals(0, limit.getResult().getOffset());
        assertEquals(Integer.MAX_VALUE, arena.getDeviceAllocatedMemoryL());
        assertEquals(Integer.MAX_VALUE, arena.getDeviceUsedMemoryL());
        GlBuffer full = arena.getBufferObject();
        arena.ensureCapacity(commands, 0);
        assertNotSame(full, arena.getBufferObject());
        assertEquals(0, limit.getResult().getOffset());
        Mockito.verify(commands).copyBufferSubData(same(full), same(arena.getBufferObject()), eq(0L), eq(0L), eq((long) Integer.MAX_VALUE));
        check(arena);
        GlBuffer buffer = arena.getBufferObject();
        List<PendingUpload> pending = queue(upload(1));
        assertThrows(OutOfMemoryError.class, () -> arena.upload(commands, pending));
        assertSame(buffer, arena.getBufferObject());
        assertEquals(Integer.MAX_VALUE, arena.getDeviceUsedMemoryL());
        check(arena);
    }

    // Verifies that upload throws when the reserve step leaves no room
    @Test
    void uploadThrowsWhenReserveLeavesNoRoom() {
        GlBufferArena arena = Mockito.spy(arena(4, 4));
        Mockito.doNothing().when(arena).reserve(any(), anyLong());
        List<PendingUpload> pending = queue(upload(64));
        assertThrows(RuntimeException.class, () -> arena.upload(commands, pending));
        assertEquals(1, pending.size());
    }

    // Verifies that ensureCapacity rejects a null command list and a negative count without any call
    @Test
    void ensureCapacityRejectsNullAndNegativeCounts() {
        GlBufferArena arena = arena(4, 64);
        assertThrows(NullPointerException.class, () -> arena.ensureCapacity(null, 1));
        assertThrows(IllegalArgumentException.class, () -> arena.ensureCapacity(commands, -1));
        assertThrows(IllegalArgumentException.class, () -> arena.ensureCapacity(commands, Integer.MIN_VALUE));
        Mockito.verifyNoInteractions(commands, staging);
    }

    // Verifies that ensureCapacity on a deleted arena is rejected without any call
    @Test
    void ensureCapacityAfterDeleteIsRejected() {
        GlBufferArena arena = arena(4, 64);
        place(arena, upload(16));
        arena.delete(commands);
        Mockito.clearInvocations(commands);
        assertThrows(IllegalStateException.class, () -> arena.ensureCapacity(commands, 1));
        Mockito.verifyNoInteractions(commands);
    }

    // Verifies that a zero count on a fresh arena replaces the empty buffer with another empty buffer
    @Test
    void zeroCountOnAFreshArenaReplacesTheEmptyBuffer() {
        GlBufferArena arena = arena(4, 64);
        GlBuffer empty = arena.getBufferObject();
        arena.ensureCapacity(commands, 0);
        assertNotSame(empty, arena.getBufferObject());
        assertEquals(Set.of(arena.getBufferObject()), live);
        assertEquals(0L, sizes.get(arena.getBufferObject()));
        assertEquals(0, arena.getDeviceAllocatedMemoryL());
        check(arena);
    }

    // Verifies that growth starts from the larger of the required elements and the capacity and compacts at half
    @Test
    void growthStartsFromTheLargerOfRequiredAndCapacity() {
        GlBufferArena arena = arena(1, 0);
        arena.ensureCapacity(commands, 8);
        assertEquals(16, capacity(arena));
        PendingUpload ten = elements(10);
        place(arena, ten);
        arena.ensureCapacity(commands, 7);
        assertEquals(34, capacity(arena));
        arena.ensureCapacity(commands, 7);
        assertEquals(34, capacity(arena));
        arena.ensureCapacity(commands, 8);
        assertEquals(68, capacity(arena));
        assertEquals(58, ten.getResult().getOffset());
        assertHolds(arena, ten);
        check(arena);
    }

    // Verifies the compaction boundary of an odd capacity
    @Test
    void oddCapacityCompactsUpToItsFloorHalf() {
        GlBufferArena arena = arena(1, 7);
        arena.ensureCapacity(commands, 3);
        assertEquals(7, capacity(arena));
        arena.ensureCapacity(commands, 3);
        assertEquals(7, capacity(arena));
        arena.ensureCapacity(commands, 4);
        assertEquals(14, capacity(arena));
    }

    // Verifies that growth doubles below four mebibytes and adds half from there up to the int limit
    @ParameterizedTest
    @CsvSource({
            "1, 4194303, 8388606",
            "1, 4194304, 6291456",
            "4, 1048575, 2097150",
            "4, 1048576, 1572864",
            "1, 1431655765, 2147483647",
            "1, 1431655766, 2147483647",
            "1, 2147483647, 2147483647",
            "2, 2147483647, 2147483647"
    })
    void growthStepFollowsTheArenaSize(int stride, int count, long expectedElements) {
        modelBytes = false;
        GlBufferArena arena = arena(stride, 0);
        arena.ensureCapacity(commands, count);
        assertEquals(expectedElements, capacity(arena));
        assertEquals(expectedElements * stride, sizes.get(arena.getBufferObject()));
    }

    // Verifies that growth never selects less than the minimum capacity
    @Test
    void growthRespectsTheMinimumCapacity() {
        GlBufferArena arena = arena(4, 1024);
        arena.ensureCapacity(commands, 1);
        assertEquals(256, capacity(arena));
    }

    // Verifies that the arena compacts at the int limit and throws once the elements pass it
    @Test
    void ensureCapacityAtTheIntLimit() {
        modelBytes = false;
        GlBufferArena arena = arena(1, 0);
        arena.ensureCapacity(commands, Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, capacity(arena));
        PendingUpload one = upload(1);
        assertFalse(place(arena, one));
        assertEquals(Integer.MAX_VALUE - 1, one.getResult().getOffset());
        arena.ensureCapacity(commands, Integer.MAX_VALUE - 1);
        assertEquals(Integer.MAX_VALUE, capacity(arena));
        assertEquals(Integer.MAX_VALUE - 1, one.getResult().getOffset());
        GlBuffer buffer = arena.getBufferObject();
        assertThrows(OutOfMemoryError.class, () -> arena.ensureCapacity(commands, Integer.MAX_VALUE));
        assertSame(buffer, arena.getBufferObject());
        assertEquals(1, used(arena));
        check(arena);
    }

    // Verifies that a failed copy deletes the new buffer and leaves the arena unchanged
    @Test
    void failedCopyLeavesTheArenaUnchanged() {
        GlBufferArena arena = arena(4, 0);
        PendingUpload a = elements(2);
        PendingUpload b = elements(2);
        PendingUpload c = elements(2);
        place(arena, a, b, c);
        b.getResult().delete();
        GlBuffer buffer = arena.getBufferObject();
        IllegalStateException failure = new IllegalStateException("Copy failed");
        Mockito.doThrow(failure).when(commands).copyBufferSubData(any(), any(), anyLong(), anyLong(), anyLong());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> arena.ensureCapacity(commands, 20)));
        assertSame(buffer, arena.getBufferObject());
        assertEquals(Set.of(buffer), live);
        assertEquals(12, capacity(arena));
        assertEquals(List.of(10, 6), offsets(a, c));
        check(arena);
        Mockito.doAnswer(copyAnswer).when(commands).copyBufferSubData(any(), any(), anyLong(), anyLong(), anyLong());
        arena.ensureCapacity(commands, 20);
        assertEquals(48, capacity(arena));
        assertEquals(List.of(46, 44), offsets(a, c));
        assertHolds(arena, a, c);
        check(arena);
    }

    // Verifies that a failed storage allocation during growth deletes the new buffer and leaves the arena unchanged
    @Test
    void failedStorageAllocationLeavesTheArenaUnchanged() {
        GlBufferArena arena = arena(4, 0);
        PendingUpload a = elements(2);
        PendingUpload b = elements(2);
        place(arena, a, b);
        GlBuffer buffer = arena.getBufferObject();
        IllegalStateException failure = new IllegalStateException("Out of memory");
        Mockito.doThrow(failure).when(commands).allocateStorage(any(), anyLong(), any());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> arena.ensureCapacity(commands, 20)));
        assertSame(buffer, arena.getBufferObject());
        assertEquals(Set.of(buffer), live);
        assertEquals(List.of(6, 4), offsets(a, b));
        assertHolds(arena, a, b);
        check(arena);
    }

    // Verifies that repeated resizes keep exactly one live buffer and the data intact
    @Test
    void repeatedResizesLeakNoBuffer() {
        GlBufferArena arena = arena(4, 0);
        List<PendingUpload> placed = new ArrayList<>();
        for (int round = 0; round < 40; round++) {
            PendingUpload upload = elements(1 + round % 5);
            place(arena, upload);
            placed.add(upload);
            if (round % 3 == 0) {
                placed.remove(0).getResult().delete();
            }
            arena.ensureCapacity(commands, round % 4 == 0 ? 50 : 0);
            assertEquals(Set.of(arena.getBufferObject()), live);
            assertHolds(arena, placed);
            check(arena);
        }
    }

    // Verifies that free rejects null and a second free of one segment
    @Test
    void freeRejectsNullAndADoubleFree() {
        GlBufferArena arena = arena(4, 0);
        PendingUpload a = elements(2);
        PendingUpload b = elements(3);
        place(arena, a, b);
        assertThrows(NullPointerException.class, () -> arena.free(null));
        a.getResult().delete();
        assertThrows(IllegalStateException.class, a.getResult()::delete);
        assertThrows(IllegalStateException.class, () -> arena.free(a.getResult()));
        assertEquals(3, used(arena));
        check(arena);
    }

    // Verifies that free merges with the previous and the next free neighbour and with both
    @Test
    void freeMergesWithFreeNeighbours() {
        GlBufferArena arena = arena(4, 0);
        PendingUpload[] singles = singles(arena);
        Consumer<Integer> free = offset -> {
            singles[offset].getResult().delete();
            check(arena);
        };
        free.accept(5);
        free.accept(7);
        free.accept(6);
        free.accept(9);
        free.accept(10);
        free.accept(13);
        free.accept(12);
        PendingUpload three = elements(3);
        PendingUpload low = elements(2);
        PendingUpload high = elements(2);
        assertFalse(place(arena, three, low, high));
        assertEquals(List.of(5, 9, 12), offsets(three, low, high));
        assertEquals(16, used(arena));
        check(arena);
    }

    // Verifies that freeing every segment in any order restores one free segment over the whole arena
    @Test
    void freeingEverySegmentInAnyOrderRestoresOneFreeSegment() {
        GlBufferArena arena = arena(4, 0);
        PendingUpload[] singles = singles(arena);
        List<PendingUpload> order = new ArrayList<>(Arrays.asList(singles));
        Collections.shuffle(order, new Random(16));
        for (PendingUpload upload : order) {
            upload.getResult().delete();
            check(arena);
        }
        assertTrue(arena.isEmpty());
        PendingUpload whole = elements(16);
        assertFalse(place(arena, whole));
        assertEquals(0, whole.getResult().getOffset());
    }

    // Verifies that a free after delete only marks the segment and makes no call
    @Test
    void freeAfterDeleteOnlyMarksTheSegment() {
        GlBufferArena arena = arena(4, 0);
        PendingUpload a = elements(2);
        PendingUpload b = elements(2);
        place(arena, a, b);
        arena.delete(commands);
        Mockito.clearInvocations(commands);
        a.getResult().delete();
        assertThrows(IllegalStateException.class, a.getResult()::delete);
        b.getResult().delete();
        Mockito.verifyNoInteractions(commands);
        assertTrue(arena.isEmpty());
        assertEquals(0, arena.getDeviceUsedMemoryL());
        check(arena);
    }

    // Verifies that a freed segment dropped by compaction stays freed
    @Test
    void segmentDroppedByCompactionStaysFreed() {
        GlBufferArena arena = arena(4, 0);
        PendingUpload a = elements(2);
        PendingUpload b = elements(2);
        place(arena, a, b);
        GlBufferSegment dropped = a.getResult();
        dropped.delete();
        arena.ensureCapacity(commands, 0);
        assertThrows(IllegalStateException.class, dropped::delete);
        assertEquals(2, used(arena));
        check(arena);
    }

    // Verifies that a segment rejects a length below one and a negative offset
    @Test
    void segmentRejectsAnEmptyLengthAndANegativeOffset() {
        GlBufferSegment segment = new GlBufferSegment(arena(4, 0), 2, 4);
        assertThrows(IllegalArgumentException.class, () -> segment.setLength(0));
        assertThrows(IllegalArgumentException.class, () -> segment.setLength(Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> segment.setOffset(-1));
        assertThrows(IllegalArgumentException.class, () -> segment.setOffset(Integer.MIN_VALUE));
        assertEquals(2, segment.getOffset());
        assertEquals(4, segment.getLength());
        assertEquals(6, segment.getEnd());
    }

    // Verifies that delete rejects null and deletes the buffer only once
    @Test
    void deleteIsIdempotentAndRejectsNull() {
        GlBufferArena arena = arena(4, 64);
        GlBuffer buffer = arena.getBufferObject();
        assertThrows(NullPointerException.class, () -> arena.delete(null));
        assertFalse(arena.isDeleted());
        arena.delete(commands);
        arena.delete(commands);
        assertThrows(NullPointerException.class, () -> arena.delete(null));
        Mockito.verify(commands).deleteBuffer(same(buffer));
        Mockito.verifyNoMoreInteractions(commands);
        assertTrue(arena.isDeleted());
        assertSame(buffer, arena.getBufferObject());
        assertTrue(live.isEmpty());
    }

    // Verifies that a delete that throws is not retried
    @Test
    void failedDeleteIsNotRetried() {
        GlBufferArena arena = arena(4, 64);
        IllegalStateException failure = new IllegalStateException("Delete failed");
        Mockito.doThrow(failure).when(commands).deleteBuffer(any());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> arena.delete(commands)));
        assertTrue(arena.isDeleted());
        arena.delete(commands);
        Mockito.verify(commands).deleteBuffer(any());
    }

    // Verifies that the segment list check catches every kind of corruption
    @Test
    void segmentListCheckCatchesCorruption() {
        Map<String, Consumer<GlBufferArena>> corruptions = new LinkedHashMap<>();
        corruptions.put("broken links", arena -> segmentAt(arena, 5).setPrev(null));
        corruptions.put("overlapping or sparse", arena -> segmentAt(arena, 15).setOffset(14));
        corruptions.put("empty segment", arena -> {
            GlBufferSegment before = segmentAt(arena, 3);
            GlBufferSegment after = segmentAt(arena, 4);
            GlBufferSegment empty = new GlBufferSegment(arena, 4, 0);
            before.setNext(empty);
            empty.setPrev(before);
            empty.setNext(after);
            after.setPrev(empty);
        });
        corruptions.put("not merged", arena -> {
            segmentAt(arena, 4).delete();
            segmentAt(arena, 5).setFree(true);
        });
        corruptions.put("not indexed", arena -> segmentAt(arena, 5).setFree(true));
        corruptions.put("do not cover", arena -> segmentAt(arena, 15).setLength(2));
        corruptions.put("outside the list", arena -> {
            GlBufferSegment stray = new GlBufferSegment(arena, 100, 1);
            stray.setFree(true);
            freeIndex(arena).add(stray);
        });
        corruptions.put("used is invalid", arena -> USED.set(arena, 15));
        for (Map.Entry<String, Consumer<GlBufferArena>> corruption : corruptions.entrySet()) {
            GlBufferArena arena = arena(1, 0);
            singles(arena);
            check(arena);
            corruption.getValue().accept(arena);
            IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> check(arena));
            assertTrue(thrown.getMessage().contains(corruption.getKey()), thrown.getMessage());
        }
    }

    // Returns the linked segment at the given offset
    private static GlBufferSegment segmentAt(GlBufferArena arena, int offset) {
        for (GlBufferSegment seg = (GlBufferSegment) HEAD.get(arena); seg != null; seg = seg.getNext()) {
            if (seg.getOffset() == offset) {
                return seg;
            }
        }
        throw new AssertionError("No segment at " + offset);
    }

    // Returns the free index of the arena
    @SuppressWarnings("unchecked")
    private static TreeSet<GlBufferSegment> freeIndex(GlBufferArena arena) {
        return (TreeSet<GlBufferSegment>) FREE_SEGMENTS.get(arena);
    }

    // Verifies that random uploads and frees and reserves place every segment where a linear best fit scan places it
    @ParameterizedTest
    @ValueSource(ints = {1, 3, 4, 20})
    void randomOperationsMatchTheLinearBestFitModel(int stride) {
        GlBufferArena arena = arena(stride, 64);
        Model model = new Model(stride, 64);
        Random random = new Random(stride * 7919L);
        List<PendingUpload> placed = new ArrayList<>();
        List<Model.Cell> cells = new ArrayList<>();
        for (int step = 0; step < 3000; step++) {
            int op = random.nextInt(10);
            if (op < 4 && placed.size() < 300) {
                int count = 1 + random.nextInt(5);
                PendingUpload[] uploads = new PendingUpload[count];
                int[] sizes = new int[count];
                for (int i = 0; i < count; i++) {
                    int length = random.nextInt(5) == 0 ? random.nextInt(stride * 4) : random.nextInt(stride * 48);
                    uploads[i] = upload(length);
                    sizes[i] = Math.ceilDiv(length, stride);
                }
                int resizes = model.resizes;
                List<Model.Cell> result = model.upload(sizes);
                assertEquals(model.resizes != resizes, place(arena, uploads));
                placed.addAll(Arrays.asList(uploads));
                cells.addAll(result);
            } else if (op < 9) {
                for (int frees = 1 + random.nextInt(2); frees > 0 && !placed.isEmpty(); frees--) {
                    int index = random.nextInt(placed.size());
                    Collections.swap(placed, index, placed.size() - 1);
                    Collections.swap(cells, index, cells.size() - 1);
                    placed.remove(placed.size() - 1).getResult().delete();
                    model.free(cells.remove(cells.size() - 1));
                }
            } else {
                int count = random.nextInt(64);
                arena.ensureCapacity(commands, count);
                model.reserve(count);
            }
            check(arena);
            assertEquals((long) model.used * stride, arena.getDeviceUsedMemoryL());
            assertEquals((long) model.capacity * stride, arena.getDeviceAllocatedMemoryL());
            for (int i = 0; i < placed.size(); i++) {
                GlBufferSegment segment = placed.get(i).getResult();
                assertEquals(cells.get(i).offset, segment.getOffset());
                assertEquals(cells.get(i).length, segment.getLength());
            }
            assertHolds(arena, placed);
            assertEquals(Set.of(arena.getBufferObject()), live);
        }
    }

    // Verifies that the arena works on the test render device with the fallback staging buffer
    @Test
    void worksOnTheTestRenderDevice() {
        GLRenderDevice device = Devices.active();
        try {
            CommandList list = device.createCommandList();
            FallbackStagingBuffer fallback = new FallbackStagingBuffer(list);
            GlBufferArena arena = new GlBufferArena(list, 4, 64, fallback);
            PendingUpload a = upload(16);
            PendingUpload b = upload(16);
            PendingUpload c = upload(16);
            assertTrue(arena.upload(list, queue(a, b, c)));
            assertEquals(96, arena.getDeviceAllocatedMemoryL());
            assertEquals(List.of(20, 16, 12), offsets(a, b, c));
            b.getResult().delete();
            PendingUpload refill = upload(16);
            assertFalse(arena.upload(list, queue(refill)));
            assertEquals(16, refill.getResult().getOffset());
            arena.ensureCapacity(list, 100);
            assertEquals(List.of(212, 216, 220), offsets(c, refill, a));
            arena.delete(list);
            arena.delete(list);
            assertTrue(arena.isDeleted());
            fallback.delete(list);
        } finally {
            device.makeInactive();
        }
    }

    // Linear scan model of the arena that predicts the placement of every allocation
    private static final class Model {
        // Segments of the model in offset order
        private final List<Cell> cells = new ArrayList<>();
        // Size of one element in bytes
        private final int stride;
        // Smallest capacity in elements that growth selects
        private final int minimum;
        // Capacity in elements
        private int capacity;
        // Elements held by live cells
        private int used;
        // Number of resizes so far
        private int resizes;

        // Creates an empty model with the stride and minimum capacity of the arena
        private Model(int stride, int minimumBytes) {
            this.stride = stride;
            this.minimum = Math.ceilDiv(minimumBytes, stride);
        }

        // Returns a live cell carved from the end of the best fitting free cell or null when none is large enough
        private Cell alloc(int size) {
            if (size == 0) {
                return new Cell(0, 0, false);
            }
            Cell best = null;
            int bestIndex = -1;
            for (int i = 0; i < cells.size(); i++) {
                Cell cell = cells.get(i);
                if (!cell.free || cell.length < size) {
                    continue;
                }
                if (cell.length == size) {
                    best = cell;
                    bestIndex = i;
                    break;
                }
                if (best == null || cell.length < best.length) {
                    best = cell;
                    bestIndex = i;
                }
            }
            if (best == null) {
                return null;
            }
            used += size;
            if (best.length == size) {
                best.free = false;
                return best;
            }
            Cell carved = new Cell(best.offset + best.length - size, size, false);
            best.length -= size;
            cells.add(bestIndex + 1, carved);
            return carved;
        }

        // Marks the cell free and merges it with free neighbours
        private void free(Cell cell) {
            cell.free = true;
            if (cell.length == 0) {
                return;
            }
            used -= cell.length;
            int index = cells.indexOf(cell);
            if (index + 1 < cells.size() && cells.get(index + 1).free) {
                cell.length += cells.remove(index + 1).length;
            }
            if (index > 0 && cells.get(index - 1).free) {
                cells.get(index - 1).length += cell.length;
                cells.remove(index);
            }
        }

        // Compacts the model when the live and requested elements fill at most half of it and grows it otherwise
        private void reserve(long count) {
            long required = used + count;
            int target = capacity;
            if (required * 2 > capacity) {
                long base = Math.max(required, capacity);
                long increment = base * stride < 4 * 1024 * 1024 ? base : base / 2;
                target = (int) Math.min(Math.max(base + increment, minimum), Integer.MAX_VALUE);
            }
            int freeLength = target - used;
            List<Cell> compacted = new ArrayList<>();
            if (freeLength > 0) {
                compacted.add(new Cell(0, freeLength, true));
            }
            int offset = freeLength;
            for (Cell cell : cells) {
                if (!cell.free) {
                    cell.offset = offset;
                    offset += cell.length;
                    compacted.add(cell);
                }
            }
            cells.clear();
            cells.addAll(compacted);
            capacity = target;
            resizes++;
        }

        // Places every size in two passes around one reserve and returns the cells in queue order
        private List<Cell> upload(int[] sizes) {
            Cell[] result = new Cell[sizes.length];
            long remaining = 0;
            for (int i = 0; i < sizes.length; i++) {
                result[i] = alloc(sizes[i]);
                if (result[i] == null) {
                    remaining += sizes[i];
                }
            }
            if (remaining > 0) {
                reserve(remaining);
                for (int i = 0; i < sizes.length; i++) {
                    if (result[i] == null) {
                        result[i] = alloc(sizes[i]);
                    }
                }
            }
            return Arrays.asList(result);
        }

        // Segment of the model
        private static final class Cell {
            // Offset in elements
            private int offset;
            // Length in elements
            private int length;
            // Flag set while the cell is free
            private boolean free;

            // Creates a cell
            private Cell(int offset, int length, boolean free) {
                this.offset = offset;
                this.length = length;
                this.free = free;
            }
        }
    }
}
