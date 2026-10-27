package com.bdmajora.impetus.engine.impl.gl.arena.staging;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapFlags;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapping;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferStorageFlags;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferUsage;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlImmutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.gl.functions.BufferCopyFunctions;
import com.bdmajora.impetus.engine.impl.gl.functions.BufferMapRangeFunctions;
import com.bdmajora.impetus.engine.impl.gl.functions.DeviceFunctions;
import com.bdmajora.impetus.engine.impl.gl.sync.GlFence;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;
import com.bdmajora.impetus.lwjgl.GLExtension;
import com.bdmajora.testing.Devices;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;

// Tests of the mapped staging buffer against a mock command list and the test render device
class MappedStagingBufferTest {
    // Usage hint of every fallback scratch upload and release
    private static final GlBufferUsage USAGE = GlBufferUsage.STREAM_COPY;
    // Storage bits the ring buffer is created with
    private static final int STORAGE_BITS = EnumBitField.of(GlBufferStorageFlags.PERSISTENT, GlBufferStorageFlags.CLIENT_STORAGE, GlBufferStorageFlags.MAP_WRITE).getBitField();
    // Access bits the ring buffer is mapped with
    private static final int MAP_BITS = EnumBitField.of(GlBufferMapFlags.PERSISTENT, GlBufferMapFlags.INVALIDATE_BUFFER, GlBufferMapFlags.WRITE, GlBufferMapFlags.EXPLICIT_FLUSH).getBitField();
    // Number of guard bytes on each side of every mapped ring
    private static final int GUARD = 16;
    // Value of every guard byte
    private static final byte GUARD_BYTE = (byte) 0xA5;
    // Size of one MiB in bytes
    private static final int MIB = 1024 * 1024;

    // Native allocations freed after each test
    private final List<ByteBuffer> allocations = new ArrayList<>();
    // Native blocks with guard bytes around each mapped ring
    private final List<ByteBuffer> guardedBlocks = new ArrayList<>();
    // Fences the mock command list hands out in creation order
    private final List<GlFence> fences = new ArrayList<>();
    // Command list mock that records every call of the staging buffer
    private CommandList commands;
    // Ring buffer the mock command list creates last
    private GlImmutableBuffer ring;
    // Mapping the mock command list hands out last
    private GlBufferMapping mapping;
    // Scratch buffer of the fallback path
    private GlMutableBuffer scratch;
    // Destination buffer of the copies
    private GlMutableBuffer dst;
    // Second destination buffer of the copies
    private GlMutableBuffer other;

    // Creates the mock command list with guarded native mappings and mock fences
    @BeforeEach
    void createCommandList() {
        scratch = new GlMutableBuffer();
        dst = new GlMutableBuffer();
        other = new GlMutableBuffer();
        commands = Mockito.mock(CommandList.class);
        Mockito.doReturn(scratch).when(commands).createMutableBuffer();
        Mockito.doAnswer(inv -> ring = new GlImmutableBuffer(inv.getArgument(1))).when(commands).createImmutableBuffer(anyLong(), any());
        Mockito.doAnswer(inv -> mapping = new GlBufferMapping(inv.getArgument(0), guarded(Math.toIntExact(inv.<Long>getArgument(2)))))
                .when(commands).mapBuffer(any(), anyLong(), anyLong(), any());
        Mockito.doAnswer(inv -> {
            GlFence fence = Mockito.mock(GlFence.class);
            fences.add(fence);
            return fence;
        }).when(commands).createFence();
    }

    // Checks the guard bytes of every mapped ring and frees the native allocations of the test
    @AfterEach
    void freeAllocations() {
        List<String> damaged = new ArrayList<>();
        for (ByteBuffer block : guardedBlocks) {
            int end = block.capacity();
            for (int i = 0; i < GUARD; i++) {
                if (block.get(i) != GUARD_BYTE || block.get(end - 1 - i) != GUARD_BYTE) {
                    damaged.add("guard byte " + i + " of a ring of " + (end - 2 * GUARD) + " bytes");
                    break;
                }
            }
            MemoryUtil.memFree(block);
        }
        guardedBlocks.clear();
        for (ByteBuffer allocation : allocations) {
            MemoryUtil.memFree(allocation);
        }
        allocations.clear();
        if (!damaged.isEmpty()) {
            fail("Writes outside the ring changed " + damaged);
        }
    }

    // Creates the staging buffer under test and clears the construction calls from the mock
    private MappedStagingBuffer staging(int capacity) {
        MappedStagingBuffer staging = new MappedStagingBuffer(commands, capacity);
        Mockito.clearInvocations(commands);
        return staging;
    }

    // Returns a zeroed native ring view with guard bytes on both sides
    private ByteBuffer guarded(int capacity) {
        ByteBuffer block = MemoryUtil.memAlloc(capacity + 2 * GUARD);
        guardedBlocks.add(block);
        MemoryUtil.memSet(MemoryUtil.memAddress(block), GUARD_BYTE, block.capacity());
        MemoryUtil.memSet(MemoryUtil.memAddress(block) + GUARD, 0, capacity);
        return MemoryUtil.memByteBuffer(MemoryUtil.memAddress(block) + GUARD, capacity);
    }

    // Allocates zeroed native memory that is freed after the test
    private ByteBuffer alloc(int bytes) {
        ByteBuffer buffer = MemoryUtil.memCalloc(bytes);
        allocations.add(buffer);
        return buffer;
    }

    // Allocates native data whose byte at each index is the seed plus the index
    private ByteBuffer data(int bytes, int seed) {
        ByteBuffer buffer = alloc(bytes);
        for (int i = 0; i < bytes; i++) {
            buffer.put(i, (byte) (seed + i));
        }
        return buffer;
    }

    // Asserts that the ring holds the given bytes of the data at the ring offset
    private void assertRing(int ringOffset, ByteBuffer data, int from, int count) {
        ByteBuffer memory = mapping.getMemoryBuffer();
        for (int i = 0; i < count; i++) {
            assertEquals(data.get(from + i), memory.get(ringOffset + i), "ring byte " + (ringOffset + i));
        }
    }

    // Asserts that every byte of the ring is zero
    private void assertRingUntouched() {
        ByteBuffer memory = mapping.getMemoryBuffer();
        for (int i = 0; i < memory.capacity(); i++) {
            assertEquals(0, memory.get(i), "ring byte " + i);
        }
    }

    // Verifies one copy from the ring into the target
    private void verifyRingCopy(GlBuffer target, long readOffset, long writeOffset, long bytes) {
        Mockito.verify(commands).copyBufferSubData(same(ring), same(target), eq(readOffset), eq(writeOffset), eq(bytes));
    }

    // Verifies one flush of a mapped ring range
    private void verifyFlushRange(int offset, int length) {
        Mockito.verify(commands).flushMappedRange(same(mapping), eq(offset), eq(length));
    }

    // Verifies one upload into the scratch buffer and its copy into the target
    private void verifyFallback(ByteBuffer data, GlBuffer target, long writeOffset, long bytes) {
        Mockito.verify(commands).uploadData(same(scratch), same(data), eq(USAGE));
        Mockito.verify(commands).copyBufferSubData(same(scratch), same(target), eq(0L), eq(writeOffset), eq(bytes));
    }

    // Marks every fence handed out so far as signalled
    private void signalAll() {
        for (GlFence fence : fences) {
            Mockito.when(fence.isCompleted()).thenReturn(true);
        }
    }

    // Signals every fence and reclaims their regions
    private void reclaim(MappedStagingBuffer staging) {
        signalAll();
        staging.flip();
    }

    // Runs the body against a spy of the command list of the active render device
    private static void onDevice(Consumer<CommandList> body) {
        GLRenderDevice device = Devices.active();
        try {
            body.accept(Mockito.spy(device.createCommandList()));
        } finally {
            device.makeInactive();
        }
    }

    // Checks that the class implements the staging buffer interface
    @Test
    void implementsStagingBuffer() {
        assertInstanceOf(StagingBuffer.class, staging(32));
    }

    // Checks that construction creates and maps the ring with its flags before it creates the scratch buffer
    @Test
    void constructorCreatesMapsAndCreatesTheFallbackInOrder() {
        new MappedStagingBuffer(commands, 32);
        InOrder order = Mockito.inOrder(commands);
        order.verify(commands).createImmutableBuffer(eq(32L), argThat(flags -> flags.getBitField() == STORAGE_BITS));
        order.verify(commands).mapBuffer(same(ring), eq(0L), eq(32L), argThat(flags -> flags.getBitField() == MAP_BITS));
        order.verify(commands).createMutableBuffer();
        Mockito.verifyNoMoreInteractions(commands);
    }

    // Checks that the default constructor creates a ring of 16 MiB
    @Test
    void defaultConstructorCreatesA16MiBRing() {
        MappedStagingBuffer staging = new MappedStagingBuffer(commands);
        Mockito.verify(commands).createImmutableBuffer(eq(16L * MIB), any());
        Mockito.verify(commands).mapBuffer(same(ring), eq(0L), eq(16L * MIB), any());
        assertEquals("Mapped (16/16 MiB)", staging.toString());
    }

    // Checks that both constructors reject a null command list
    @Test
    void constructorRejectsNullCommandList() {
        assertThrows(NullPointerException.class, () -> new MappedStagingBuffer(null));
        assertThrows(NullPointerException.class, () -> new MappedStagingBuffer(null, 32));
        assertThrows(NullPointerException.class, () -> new MappedStagingBuffer(null, 0));
    }

    // Checks that zero and negative capacities are rejected before any call
    @Test
    void constructorRejectsZeroAndNegativeCapacity() {
        for (int capacity : new int[]{0, -1, -32, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new MappedStagingBuffer(commands, capacity));
        }
        Mockito.verifyNoInteractions(commands);
    }

    // Checks that a failed creation propagates without any other call
    @Test
    void constructorPropagatesACreateFailure() {
        IllegalStateException failure = new IllegalStateException("create");
        Mockito.doThrow(failure).when(commands).createImmutableBuffer(anyLong(), any());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> new MappedStagingBuffer(commands, 32)));
        Mockito.verify(commands).createImmutableBuffer(eq(32L), any());
        Mockito.verifyNoMoreInteractions(commands);
    }

    // Checks that a failed mapping frees the ring buffer and propagates
    @Test
    void constructorFreesTheRingWhenMappingFails() {
        RuntimeException failure = new RuntimeException("Failed to map buffer");
        Mockito.doThrow(failure).when(commands).mapBuffer(any(), anyLong(), anyLong(), any());
        assertSame(failure, assertThrows(RuntimeException.class, () -> new MappedStagingBuffer(commands, 32)));
        InOrder order = Mockito.inOrder(commands);
        order.verify(commands).createImmutableBuffer(eq(32L), any());
        order.verify(commands).mapBuffer(same(ring), eq(0L), eq(32L), any());
        order.verify(commands).deleteBuffer(same(ring));
        Mockito.verifyNoMoreInteractions(commands);
    }

    // Checks that a failed scratch buffer creation unmaps and frees the ring buffer and propagates
    @Test
    void constructorFreesTheRingWhenTheFallbackFails() {
        IllegalStateException failure = new IllegalStateException("scratch");
        Mockito.doThrow(failure).when(commands).createMutableBuffer();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> new MappedStagingBuffer(commands, 32)));
        InOrder order = Mockito.inOrder(commands);
        order.verify(commands).mapBuffer(same(ring), eq(0L), eq(32L), any());
        order.verify(commands).createMutableBuffer();
        order.verify(commands).unmap(same(mapping));
        order.verify(commands).deleteBuffer(same(ring));
    }

    // Checks that a ring of the largest int size is created and written from its start
    @Test
    void constructorAcceptsTheIntLimitCapacity() {
        ByteBuffer block = alloc(64);
        Mockito.doAnswer(inv -> mapping = new GlBufferMapping(inv.getArgument(0), MemoryUtil.memByteBuffer(MemoryUtil.memAddress(block), Integer.MAX_VALUE)))
                .when(commands).mapBuffer(any(), anyLong(), anyLong(), any());
        MappedStagingBuffer staging = new MappedStagingBuffer(commands, Integer.MAX_VALUE);
        Mockito.verify(commands).createImmutableBuffer(eq((long) Integer.MAX_VALUE), any());
        Mockito.verify(commands).mapBuffer(same(ring), eq(0L), eq((long) Integer.MAX_VALUE), any());
        assertEquals("Mapped (2047/2047 MiB)", staging.toString());
        ByteBuffer data = data(48, 1);
        staging.enqueueCopy(commands, data, dst, 0);
        staging.flush(commands);
        verifyFlushRange(0, 48);
        verifyRingCopy(dst, 0, 0, 48);
        for (int i = 0; i < 48; i++) {
            assertEquals(data.get(i), block.get(i));
        }
        assertEquals(0, block.get(48));
    }

    // Checks that a ring of one byte stages one byte at a time and wraps back to its start
    @Test
    void constructorAcceptsAOneByteRing() {
        MappedStagingBuffer staging = staging(1);
        ByteBuffer first = data(1, 7);
        ByteBuffer second = data(1, 9);
        staging.enqueueCopy(commands, first, dst, 0);
        assertRing(0, first, 0, 1);
        staging.enqueueCopy(commands, second, dst, 1);
        verifyFlushRange(0, 1);
        verifyRingCopy(dst, 0, 0, 1);
        verifyFallback(second, dst, 1, 1);
        staging.flush(commands);
        reclaim(staging);
        staging.enqueueCopy(commands, second, dst, 2);
        assertRing(0, second, 0, 1);
        staging.flush(commands);
        Mockito.verify(commands, Mockito.times(2)).flushMappedRange(same(mapping), eq(0), eq(1));
        verifyRingCopy(dst, 0, 2, 1);
    }

    // Checks the support answer for every function table and context combination
    @Test
    void isSupportedMatchesEveryFunctionAndContextCombination() {
        RenderDevice device = Mockito.mock(RenderDevice.class);
        for (BufferCopyFunctions copy : BufferCopyFunctions.values()) {
            for (BufferMapRangeFunctions map : BufferMapRangeFunctions.values()) {
                for (boolean version : new boolean[]{false, true}) {
                    for (boolean extension : new boolean[]{false, true}) {
                        Mockito.when(device.getDeviceFunctions()).thenReturn(new DeviceFunctions(copy, map));
                        Mockito.when(TestGl.gl().isOpenGLVersionSupported(4, 4)).thenReturn(version);
                        Mockito.when(TestGl.gl().isExtensionSupported(GLExtension.ARB_buffer_storage)).thenReturn(extension);
                        boolean expected = copy != BufferCopyFunctions.PIXEL_PACK && map == BufferMapRangeFunctions.CORE && (version || extension);
                        assertEquals(expected, MappedStagingBuffer.isSupported(device), copy + " " + map + " " + version + " " + extension);
                        assertEquals(expected, MappedStagingBuffer.isSupported(device));
                    }
                }
            }
        }
    }

    // Checks that the context is queried only when the function table allows the ring
    @Test
    void isSupportedQueriesTheContextOnlyWhenTheFunctionsAllowIt() {
        RenderDevice device = Mockito.mock(RenderDevice.class);
        Mockito.clearInvocations(TestGl.gl());
        Mockito.when(device.getDeviceFunctions()).thenReturn(new DeviceFunctions(BufferCopyFunctions.PIXEL_PACK, BufferMapRangeFunctions.CORE));
        assertFalse(MappedStagingBuffer.isSupported(device));
        Mockito.when(device.getDeviceFunctions()).thenReturn(new DeviceFunctions(BufferCopyFunctions.CORE, BufferMapRangeFunctions.MAP_FULL_AND_SLICE));
        assertFalse(MappedStagingBuffer.isSupported(device));
        Mockito.verify(TestGl.gl(), Mockito.never()).isOpenGLVersionSupported(anyInt(), anyInt());
        Mockito.verify(TestGl.gl(), Mockito.never()).isExtensionSupported(any());
        Mockito.when(device.getDeviceFunctions()).thenReturn(new DeviceFunctions(BufferCopyFunctions.CORE, BufferMapRangeFunctions.CORE));
        assertTrue(MappedStagingBuffer.isSupported(device));
        Mockito.verify(TestGl.gl()).isOpenGLVersionSupported(4, 4);
        Mockito.verify(TestGl.gl(), Mockito.never()).isExtensionSupported(any());
    }

    // Checks that a null device is rejected
    @Test
    void isSupportedRejectsNullDevice() {
        assertThrows(NullPointerException.class, () -> MappedStagingBuffer.isSupported(null));
    }

    // Checks that a copy writes into the ring at once and reaches the command list only on flush
    @Test
    void enqueueCopyWritesIntoTheRingAndDefersTheCopyToFlush() {
        MappedStagingBuffer staging = staging(32);
        ByteBuffer data = data(8, 10);
        staging.enqueueCopy(commands, data, dst, 16);
        Mockito.verifyNoInteractions(commands);
        assertRing(0, data, 0, 8);
        staging.flush(commands);
        InOrder order = Mockito.inOrder(commands);
        order.verify(commands).flushMappedRange(same(mapping), eq(0), eq(8));
        order.verify(commands).copyBufferSubData(same(ring), same(dst), eq(0L), eq(16L), eq(8L));
        order.verify(commands).createFence();
        Mockito.verifyNoMoreInteractions(commands);
        assertEquals(0, data.position());
        assertEquals(8, data.limit());
    }

    // Checks that a single byte is staged and copied
    @Test
    void enqueueCopyMovesASingleByte() {
        MappedStagingBuffer staging = staging(32);
        ByteBuffer data = data(1, 42);
        staging.enqueueCopy(commands, data, dst, 0);
        assertRing(0, data, 0, 1);
        assertEquals(0, mapping.getMemoryBuffer().get(1));
        staging.flush(commands);
        verifyFlushRange(0, 1);
        verifyRingCopy(dst, 0, 0, 1);
    }

    // Checks that empty or drained data makes no call and takes no ring space
    @Test
    void enqueueCopySkipsEmptyData() {
        MappedStagingBuffer staging = staging(32);
        staging.enqueueCopy(commands, ByteBuffer.allocateDirect(0), dst, 0);
        ByteBuffer drained = data(8, 1);
        drained.position(8);
        staging.enqueueCopy(commands, drained, dst, 64);
        staging.enqueueCopy(commands, ByteBuffer.allocateDirect(0), dst, Long.MAX_VALUE);
        staging.flush(commands);
        staging.flip();
        Mockito.verifyNoInteractions(commands);
        assertEquals(8, drained.position());
        assertRingUntouched();
        staging.enqueueCopy(commands, data(32, 3), dst, 0);
        Mockito.verifyNoInteractions(commands);
    }

    // Checks that only the bytes between position and limit are staged and that the bounds of the data stay unchanged
    @Test
    void enqueueCopyReadsFromPositionToLimitAndKeepsTheBounds() {
        MappedStagingBuffer staging = staging(32);
        ByteBuffer data = data(16, 50);
        data.position(3).limit(10);
        staging.enqueueCopy(commands, data, dst, 5);
        assertRing(0, data, 3, 7);
        assertEquals(0, mapping.getMemoryBuffer().get(7));
        staging.flush(commands);
        verifyFlushRange(0, 7);
        verifyRingCopy(dst, 0, 5, 7);
        assertEquals(3, data.position());
        assertEquals(10, data.limit());
        assertEquals(16, data.capacity());
    }

    // Checks that a copy past the end of the ring is split into its part before the end and its part from the start
    @Test
    void enqueueCopySplitsAtTheEndOfTheRing() {
        MappedStagingBuffer staging = staging(32);
        staging.enqueueCopy(commands, data(24, 0), dst, 0);
        staging.flush(commands);
        reclaim(staging);
        Mockito.clearInvocations(commands);
        ByteBuffer data = data(16, 100);
        staging.enqueueCopy(commands, data, other, 50);
        assertRing(24, data, 0, 8);
        assertRing(0, data, 8, 8);
        staging.flush(commands);
        InOrder order = Mockito.inOrder(commands);
        order.verify(commands).flushMappedRange(same(mapping), eq(24), eq(8));
        order.verify(commands).flushMappedRange(same(mapping), eq(0), eq(8));
        order.verify(commands).copyBufferSubData(same(ring), same(other), eq(24L), eq(50L), eq(8L));
        order.verify(commands).copyBufferSubData(same(ring), same(other), eq(0L), eq(58L), eq(8L));
        order.verify(commands).createFence();
        Mockito.verifyNoMoreInteractions(commands);
    }

    // Checks that a split copy reads both parts from the position of the data
    @Test
    void enqueueCopyWrapsDataThatStartsPastItsFirstByte() {
        MappedStagingBuffer staging = staging(32);
        staging.enqueueCopy(commands, data(24, 0), dst, 0);
        staging.flush(commands);
        reclaim(staging);
        ByteBuffer data = data(20, 100);
        data.position(4);
        staging.enqueueCopy(commands, data, other, 50);
        assertRing(24, data, 4, 8);
        assertRing(0, data, 12, 8);
        assertEquals(4, data.position());
        staging.flush(commands);
        verifyRingCopy(other, 24, 50, 8);
        verifyRingCopy(other, 0, 58, 8);
    }

    // Checks that a copy ending at the end of the ring is not split and that the next copy starts the ring again
    @Test
    void enqueueCopyEndingAtTheRingEndMakesNoEmptyCopy() {
        MappedStagingBuffer staging = staging(32);
        ByteBuffer full = data(32, 1);
        staging.enqueueCopy(commands, full, dst, 0);
        assertRing(0, full, 0, 32);
        staging.flush(commands);
        verifyFlushRange(0, 32);
        verifyRingCopy(dst, 0, 0, 32);
        reclaim(staging);
        ByteBuffer next = data(8, 90);
        staging.enqueueCopy(commands, next, dst, 100);
        assertRing(0, next, 0, 8);
        staging.flush(commands);
        verifyFlushRange(0, 8);
        verifyRingCopy(dst, 0, 100, 8);
        Mockito.verify(commands, Mockito.never()).copyBufferSubData(any(), any(), anyLong(), anyLong(), eq(0L));
        Mockito.verify(commands, Mockito.never()).flushMappedRange(any(), anyInt(), eq(0));
    }

    // Checks that a batch filling the whole ring from an offset flushes and copies every byte
    @Test
    void enqueueCopyFillingTheWholeRingFromAnOffsetFlushesEveryByte() {
        MappedStagingBuffer staging = staging(32);
        staging.enqueueCopy(commands, data(8, 0), dst, 0);
        staging.flush(commands);
        reclaim(staging);
        Mockito.clearInvocations(commands);
        ByteBuffer full = data(32, 7);
        staging.enqueueCopy(commands, full, dst, 0);
        assertRing(8, full, 0, 24);
        assertRing(0, full, 24, 8);
        staging.flush(commands);
        verifyFlushRange(8, 24);
        verifyFlushRange(0, 8);
        verifyRingCopy(dst, 8, 0, 24);
        verifyRingCopy(dst, 0, 24, 8);
        reclaim(staging);
        for (int i = 0; i < 4; i++) {
            staging.enqueueCopy(commands, data(8, i), other, 200 + 8L * i);
        }
        staging.flush(commands);
        Mockito.verify(commands, Mockito.times(2)).flushMappedRange(same(mapping), eq(8), eq(24));
        Mockito.verify(commands, Mockito.times(2)).flushMappedRange(same(mapping), eq(0), eq(8));
        verifyRingCopy(other, 8, 200, 24);
        verifyRingCopy(other, 0, 224, 8);
    }

    // Checks that contiguous copies to one destination merge and that gaps or other destinations start new copies
    @Test
    void enqueueCopyCoalescesContiguousCopies() {
        MappedStagingBuffer staging = staging(64);
        staging.enqueueCopy(commands, data(8, 0), dst, 0);
        staging.enqueueCopy(commands, data(8, 8), dst, 8);
        staging.enqueueCopy(commands, data(4, 16), dst, 16);
        staging.enqueueCopy(commands, data(4, 20), dst, 40);
        staging.enqueueCopy(commands, data(4, 24), other, 44);
        staging.enqueueCopy(commands, data(4, 28), other, 48);
        staging.enqueueCopy(commands, data(4, 32), other, 0);
        staging.flush(commands);
        verifyFlushRange(0, 36);
        verifyRingCopy(dst, 0, 0, 20);
        verifyRingCopy(dst, 20, 40, 4);
        verifyRingCopy(other, 24, 44, 8);
        verifyRingCopy(other, 32, 0, 4);
        Mockito.verify(commands, Mockito.times(4)).copyBufferSubData(any(), any(), anyLong(), anyLong(), anyLong());
        for (int i = 0; i < 36; i++) {
            assertEquals((byte) i, mapping.getMemoryBuffer().get(i));
        }
    }

    // Checks that a copy continuing one from an earlier flush starts a new copy
    @Test
    void enqueueCopyDoesNotExtendACopyFromAnEarlierFlush() {
        MappedStagingBuffer staging = staging(32);
        staging.enqueueCopy(commands, data(8, 0), dst, 0);
        staging.flush(commands);
        staging.enqueueCopy(commands, data(8, 8), dst, 8);
        staging.flush(commands);
        verifyRingCopy(dst, 0, 0, 8);
        verifyRingCopy(dst, 8, 8, 8);
        verifyFlushRange(0, 8);
        verifyFlushRange(8, 8);
        Mockito.verify(commands, Mockito.times(2)).createFence();
    }

    // Checks that each flush issues only the copies staged after the previous flush when fewer or more are staged
    @Test
    void enqueueCopyIssuesOnlyTheCopiesOfTheCurrentBatch() {
        MappedStagingBuffer staging = staging(64);
        staging.enqueueCopy(commands, data(4, 0), dst, 0);
        staging.enqueueCopy(commands, data(4, 0), dst, 100);
        staging.enqueueCopy(commands, data(4, 0), other, 0);
        staging.flush(commands);
        Mockito.verify(commands, Mockito.times(3)).copyBufferSubData(any(), any(), anyLong(), anyLong(), anyLong());
        Mockito.clearInvocations(commands);
        staging.enqueueCopy(commands, data(4, 0), other, 300);
        staging.flush(commands);
        verifyRingCopy(other, 12, 300, 4);
        Mockito.verify(commands).copyBufferSubData(any(), any(), anyLong(), anyLong(), anyLong());
        Mockito.clearInvocations(commands);
        for (int i = 0; i < 5; i++) {
            staging.enqueueCopy(commands, data(4, 0), i % 2 == 0 ? dst : other, 1000L * i);
        }
        staging.flush(commands);
        for (int i = 0; i < 5; i++) {
            verifyRingCopy(i % 2 == 0 ? dst : other, 16 + 4L * i, 1000L * i, 4);
        }
        Mockito.verify(commands, Mockito.times(5)).copyBufferSubData(any(), any(), anyLong(), anyLong(), anyLong());
    }

    // Checks that data larger than the free ring space is uploaded through the scratch buffer at once
    @Test
    void enqueueCopyFallsBackWhenTheDataExceedsTheFreeSpace() {
        MappedStagingBuffer staging = staging(32);
        ByteBuffer data = data(33, 0);
        staging.enqueueCopy(commands, data, dst, 5);
        InOrder order = Mockito.inOrder(commands);
        order.verify(commands).uploadData(same(scratch), same(data), eq(USAGE));
        order.verify(commands).copyBufferSubData(same(scratch), same(dst), eq(0L), eq(5L), eq(33L));
        Mockito.verifyNoMoreInteractions(commands);
        assertRingUntouched();
        staging.flush(commands);
        Mockito.verify(commands).allocateStorage(same(scratch), eq(0L), eq(USAGE));
        Mockito.verifyNoMoreInteractions(commands);
        assertEquals("Mapped (0/0 MiB)", staging.toString());
    }

    // Checks that data filling the last free ring byte stays in the ring and that one more byte falls back
    @Test
    void enqueueCopyUsesTheRingUpToTheLastFreeByte() {
        MappedStagingBuffer staging = staging(32);
        ByteBuffer first = data(20, 0);
        ByteBuffer second = data(12, 20);
        staging.enqueueCopy(commands, first, dst, 0);
        staging.enqueueCopy(commands, second, dst, 20);
        Mockito.verifyNoInteractions(commands);
        assertRing(0, first, 0, 20);
        assertRing(20, second, 0, 12);
        ByteBuffer one = data(1, 0);
        staging.enqueueCopy(commands, one, dst, 32);
        verifyFlushRange(0, 32);
        verifyRingCopy(dst, 0, 0, 32);
        verifyFallback(one, dst, 32, 1);
    }

    // Checks that a fallback copy is issued after the ring copies staged before it
    @Test
    void enqueueCopyFallbackKeepsEnqueueOrder() {
        MappedStagingBuffer staging = staging(32);
        ByteBuffer small = data(8, 0);
        ByteBuffer big = data(40, 0);
        staging.enqueueCopy(commands, small, dst, 0);
        staging.enqueueCopy(commands, big, dst, 0);
        InOrder order = Mockito.inOrder(commands);
        order.verify(commands).flushMappedRange(same(mapping), eq(0), eq(8));
        order.verify(commands).copyBufferSubData(same(ring), same(dst), eq(0L), eq(0L), eq(8L));
        order.verify(commands).createFence();
        order.verify(commands).uploadData(same(scratch), same(big), eq(USAGE));
        order.verify(commands).copyBufferSubData(same(scratch), same(dst), eq(0L), eq(0L), eq(40L));
        staging.flush(commands);
        order.verify(commands).allocateStorage(same(scratch), eq(0L), eq(USAGE));
        Mockito.verifyNoMoreInteractions(commands);
    }

    // Checks that a fallback copy without staged ring copies makes no ring call
    @Test
    void enqueueCopyFallbackWithoutStagedCopiesMakesNoRingCall() {
        MappedStagingBuffer staging = staging(32);
        staging.enqueueCopy(commands, data(40, 0), dst, 0);
        staging.enqueueCopy(commands, data(64, 0), dst, 40);
        Mockito.verify(commands, Mockito.never()).flushMappedRange(any(), anyInt(), anyInt());
        Mockito.verify(commands, Mockito.never()).createFence();
        Mockito.verify(commands, Mockito.never()).copyBufferSubData(same(ring), any(), anyLong(), anyLong(), anyLong());
    }

    // Checks that a failed fallback upload propagates after the staged ring copies are fenced and leaves the ring usable
    @Test
    void enqueueCopyFallbackFailureKeepsTheSubmittedBatch() {
        MappedStagingBuffer staging = staging(32);
        staging.enqueueCopy(commands, data(8, 0), dst, 0);
        IllegalStateException failure = new IllegalStateException("upload");
        Mockito.doThrow(failure).when(commands).uploadData(any(GlMutableBuffer.class), any(ByteBuffer.class), any(GlBufferUsage.class));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> staging.enqueueCopy(commands, data(40, 0), dst, 8)));
        verifyFlushRange(0, 8);
        verifyRingCopy(dst, 0, 0, 8);
        Mockito.verify(commands).createFence();
        Mockito.clearInvocations(commands);
        ByteBuffer next = data(8, 30);
        staging.enqueueCopy(commands, next, dst, 48);
        assertRing(8, next, 0, 8);
        staging.flush(commands);
        verifyFlushRange(8, 8);
        verifyRingCopy(dst, 8, 48, 8);
        Mockito.verify(commands).allocateStorage(same(scratch), eq(0L), eq(USAGE));
    }

    // Checks that null arguments are rejected before any call or ring write
    @Test
    void enqueueCopyRejectsNullArguments() {
        MappedStagingBuffer staging = staging(32);
        ByteBuffer data = data(8, 1);
        ByteBuffer big = data(40, 1);
        ByteBuffer empty = ByteBuffer.allocateDirect(0);
        assertThrows(NullPointerException.class, () -> staging.enqueueCopy(null, data, dst, 0));
        assertThrows(NullPointerException.class, () -> staging.enqueueCopy(commands, null, dst, 0));
        assertThrows(NullPointerException.class, () -> staging.enqueueCopy(commands, data, null, 0));
        assertThrows(NullPointerException.class, () -> staging.enqueueCopy(null, big, dst, 0));
        assertThrows(NullPointerException.class, () -> staging.enqueueCopy(commands, big, null, 0));
        assertThrows(NullPointerException.class, () -> staging.enqueueCopy(null, empty, dst, 0));
        assertThrows(NullPointerException.class, () -> staging.enqueueCopy(commands, empty, null, 0));
        assertThrows(NullPointerException.class, () -> staging.enqueueCopy(null, null, null, -1));
        staging.flush(commands);
        Mockito.verifyNoInteractions(commands);
        assertRingUntouched();
    }

    // Checks that heap data is rejected before any call or ring write
    @Test
    void enqueueCopyRejectsHeapData() {
        MappedStagingBuffer staging = staging(32);
        ByteBuffer[] heaps = {ByteBuffer.allocate(8), ByteBuffer.allocate(0), ByteBuffer.allocate(64), ByteBuffer.wrap(new byte[4], 1, 2), ByteBuffer.allocate(4).asReadOnlyBuffer()};
        for (ByteBuffer heap : heaps) {
            assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, heap, dst, 0));
        }
        staging.flush(commands);
        Mockito.verifyNoInteractions(commands);
        assertRingUntouched();
    }

    // Checks that read only direct data is staged
    @Test
    void enqueueCopyAcceptsReadOnlyDirectData() {
        MappedStagingBuffer staging = staging(32);
        ByteBuffer data = data(4, 60).asReadOnlyBuffer();
        staging.enqueueCopy(commands, data, dst, 12);
        assertRing(0, data, 0, 4);
        staging.flush(commands);
        verifyRingCopy(dst, 0, 12, 4);
    }

    // Checks that negative write offsets are rejected before any call for empty and filled data
    @Test
    void enqueueCopyRejectsNegativeWriteOffsets() {
        MappedStagingBuffer staging = staging(32);
        ByteBuffer empty = ByteBuffer.allocateDirect(0);
        ByteBuffer data = data(8, 1);
        ByteBuffer big = data(40, 1);
        for (long offset : new long[]{-1, -8, Integer.MIN_VALUE, Long.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, empty, dst, offset));
            assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, data, dst, offset));
            assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, big, dst, offset));
        }
        staging.flush(commands);
        Mockito.verifyNoInteractions(commands);
        assertRingUntouched();
    }

    // Checks that a write end exactly at the long limit is accepted
    @Test
    void enqueueCopyAcceptsAWriteEndAtTheLongLimit() {
        MappedStagingBuffer staging = staging(32);
        staging.enqueueCopy(commands, data(8, 0), dst, Long.MAX_VALUE - 8);
        staging.enqueueCopy(commands, data(1, 0), dst, Long.MAX_VALUE - 1);
        staging.flush(commands);
        verifyRingCopy(dst, 0, Long.MAX_VALUE - 8, 8);
        verifyRingCopy(dst, 8, Long.MAX_VALUE - 1, 1);
    }

    // Checks that a write end past the long limit is rejected before any call
    @Test
    void enqueueCopyRejectsAWriteEndPastTheLongLimit() {
        MappedStagingBuffer staging = staging(32);
        ByteBuffer data = data(8, 0);
        ByteBuffer one = data(1, 0);
        assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, data, dst, Long.MAX_VALUE - 7));
        assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, data, dst, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, one, dst, Long.MAX_VALUE));
        staging.flush(commands);
        Mockito.verifyNoInteractions(commands);
        assertRingUntouched();
    }

    // Checks that write offsets past the int limit reach the copy without truncation
    @Test
    void enqueueCopyKeepsWriteOffsetsPastTheIntLimit() {
        MappedStagingBuffer staging = staging(32);
        staging.enqueueCopy(commands, data(8, 0), dst, Integer.MAX_VALUE + 1L);
        staging.enqueueCopy(commands, data(8, 0), dst, 1L << 40);
        staging.flush(commands);
        verifyRingCopy(dst, 0, Integer.MAX_VALUE + 1L, 8);
        verifyRingCopy(dst, 8, 1L << 40, 8);
    }

    // Checks that data of the largest int length falls back after the staged ring copies and is bounded by the long limit
    @Test
    void enqueueCopyHandlesTheLargestIntLength() {
        MappedStagingBuffer staging = staging(32);
        ByteBuffer block = alloc(1);
        ByteBuffer huge = MemoryUtil.memByteBuffer(MemoryUtil.memAddress(block), Integer.MAX_VALUE);
        long lastStart = Long.MAX_VALUE - Integer.MAX_VALUE;
        staging.enqueueCopy(commands, data(4, 0), dst, 0);
        staging.enqueueCopy(commands, huge, dst, lastStart);
        InOrder order = Mockito.inOrder(commands);
        order.verify(commands).copyBufferSubData(same(ring), same(dst), eq(0L), eq(0L), eq(4L));
        order.verify(commands).uploadData(same(scratch), same(huge), eq(USAGE));
        order.verify(commands).copyBufferSubData(same(scratch), same(dst), eq(0L), eq(lastStart), eq((long) Integer.MAX_VALUE));
        Mockito.clearInvocations(commands);
        assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, huge, dst, lastStart + 1));
        assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, huge, dst, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(commands, huge, dst, Long.MIN_VALUE));
        Mockito.verifyNoInteractions(commands);
    }

    // Checks that data at odd native addresses lands at odd ring offsets and splits at the end of an odd ring
    @Test
    void enqueueCopyPassesMisalignedDataAndOffsetsThrough() {
        MappedStagingBuffer staging = staging(31);
        ByteBuffer block = data(32, 0);
        ByteBuffer odd = MemoryUtil.memByteBuffer(MemoryUtil.memAddress(block) + 1, 15);
        ByteBuffer three = MemoryUtil.memByteBuffer(MemoryUtil.memAddress(block) + 3, 3);
        assertEquals(1L, MemoryUtil.memAddress(odd) & 7L);
        staging.enqueueCopy(commands, odd, dst, 1);
        staging.enqueueCopy(commands, three, dst, 7);
        assertRing(0, odd, 0, 15);
        assertRing(15, three, 0, 3);
        staging.flush(commands);
        verifyFlushRange(0, 18);
        verifyRingCopy(dst, 0, 1, 15);
        verifyRingCopy(dst, 15, 7, 3);
        reclaim(staging);
        staging.enqueueCopy(commands, odd, other, 4097);
        assertRing(18, odd, 0, 13);
        assertRing(0, odd, 13, 2);
        staging.flush(commands);
        verifyFlushRange(18, 13);
        verifyFlushRange(0, 2);
        verifyRingCopy(other, 18, 4097, 13);
        verifyRingCopy(other, 0, 4110, 2);
    }

    // Checks that copies after delete are rejected before any call or ring write
    @Test
    void enqueueCopyRejectsUseAfterDelete() {
        MappedStagingBuffer staging = staging(32);
        staging.delete(commands);
        Mockito.clearInvocations(commands);
        assertThrows(IllegalStateException.class, () -> staging.enqueueCopy(commands, data(8, 1), dst, 0));
        assertThrows(IllegalStateException.class, () -> staging.enqueueCopy(commands, data(40, 1), dst, 0));
        assertThrows(IllegalStateException.class, () -> staging.enqueueCopy(commands, ByteBuffer.allocateDirect(0), dst, 0));
        Mockito.verifyNoInteractions(commands);
        assertRingUntouched();
    }

    // Checks that flush without staged copies makes no call
    @Test
    void flushWithoutStagedCopiesMakesNoCall() {
        MappedStagingBuffer staging = staging(32);
        staging.flush(commands);
        staging.flush(commands);
        Mockito.verifyNoInteractions(commands);
        staging.enqueueCopy(commands, data(8, 0), dst, 0);
        staging.flush(commands);
        Mockito.clearInvocations(commands);
        staging.flush(commands);
        staging.flush(commands);
        Mockito.verifyNoInteractions(commands);
    }

    // Checks that flush rejects a null command list with or without staged copies
    @Test
    void flushRejectsNullCommandList() {
        MappedStagingBuffer staging = staging(32);
        assertThrows(NullPointerException.class, () -> staging.flush(null));
        staging.enqueueCopy(commands, data(8, 0), dst, 0);
        assertThrows(NullPointerException.class, () -> staging.flush(null));
        Mockito.verifyNoInteractions(commands);
        staging.flush(commands);
        verifyFlushRange(0, 8);
        verifyRingCopy(dst, 0, 0, 8);
    }

    // Checks that flush after delete is rejected before any call with or without staged copies
    @Test
    void flushRejectsUseAfterDelete() {
        MappedStagingBuffer fresh = staging(32);
        fresh.delete(commands);
        MappedStagingBuffer staged = staging(32);
        staged.enqueueCopy(commands, data(8, 0), dst, 0);
        staged.delete(commands);
        Mockito.clearInvocations(commands);
        assertThrows(IllegalStateException.class, () -> fresh.flush(commands));
        assertThrows(IllegalStateException.class, () -> staged.flush(commands));
        Mockito.verifyNoInteractions(commands);
    }

    // Checks that flush releases the fallback scratch storage before it issues the ring copies
    @Test
    void flushReleasesTheFallbackScratchBeforeTheRingCopies() {
        MappedStagingBuffer staging = staging(32);
        staging.enqueueCopy(commands, data(40, 0), dst, 0);
        staging.enqueueCopy(commands, data(8, 0), dst, 40);
        Mockito.clearInvocations(commands);
        staging.flush(commands);
        InOrder order = Mockito.inOrder(commands);
        order.verify(commands).allocateStorage(same(scratch), eq(0L), eq(USAGE));
        order.verify(commands).flushMappedRange(same(mapping), eq(0), eq(8));
        order.verify(commands).copyBufferSubData(same(ring), same(dst), eq(0L), eq(40L), eq(8L));
        order.verify(commands).createFence();
        Mockito.verifyNoMoreInteractions(commands);
    }

    // Checks that a failed scratch release propagates before the ring copies and that the next flush issues them
    @Test
    void flushKeepsTheRingCopiesWhenTheScratchReleaseFails() {
        MappedStagingBuffer staging = staging(32);
        staging.enqueueCopy(commands, data(40, 0), dst, 0);
        staging.enqueueCopy(commands, data(8, 0), dst, 40);
        IllegalStateException failure = new IllegalStateException("release");
        Mockito.doThrow(failure).doNothing().when(commands).allocateStorage(any(), anyLong(), any());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> staging.flush(commands)));
        Mockito.verify(commands, Mockito.never()).flushMappedRange(any(), anyInt(), anyInt());
        Mockito.verify(commands, Mockito.never()).createFence();
        staging.flush(commands);
        Mockito.verify(commands, Mockito.times(2)).allocateStorage(same(scratch), eq(0L), eq(USAGE));
        verifyFlushRange(0, 8);
        verifyRingCopy(dst, 0, 40, 8);
        Mockito.verify(commands).createFence();
    }

    // Checks that a failed copy leaves the batch staged and that the next flush issues it under one fence
    @Test
    void flushRetriesTheBatchAfterAFailedCopy() {
        MappedStagingBuffer staging = staging(32);
        staging.enqueueCopy(commands, data(8, 0), dst, 0);
        staging.enqueueCopy(commands, data(8, 0), other, 0);
        IllegalStateException failure = new IllegalStateException("copy");
        Mockito.doThrow(failure).doNothing().when(commands).copyBufferSubData(any(), any(), anyLong(), anyLong(), anyLong());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> staging.flush(commands)));
        Mockito.verify(commands, Mockito.never()).createFence();
        staging.flush(commands);
        Mockito.verify(commands, Mockito.times(2)).flushMappedRange(same(mapping), eq(0), eq(16));
        Mockito.verify(commands, Mockito.times(2)).copyBufferSubData(same(ring), same(dst), eq(0L), eq(0L), eq(8L));
        verifyRingCopy(other, 8, 0, 8);
        Mockito.verify(commands).createFence();
        reclaim(staging);
        Mockito.clearInvocations(commands);
        staging.enqueueCopy(commands, data(32, 0), dst, 0);
        Mockito.verifyNoInteractions(commands);
    }

    // Checks that a failed fence leaves the batch staged and that the next flush fences it once
    @Test
    void flushRetriesTheBatchAfterAFailedFence() {
        MappedStagingBuffer staging = staging(32);
        staging.enqueueCopy(commands, data(12, 0), dst, 0);
        IllegalStateException failure = new IllegalStateException("fence");
        Mockito.doThrow(failure).doAnswer(inv -> {
            GlFence fence = Mockito.mock(GlFence.class);
            fences.add(fence);
            return fence;
        }).when(commands).createFence();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> staging.flush(commands)));
        staging.flush(commands);
        Mockito.verify(commands, Mockito.times(2)).flushMappedRange(same(mapping), eq(0), eq(12));
        Mockito.verify(commands, Mockito.times(2)).copyBufferSubData(same(ring), same(dst), eq(0L), eq(0L), eq(12L));
        assertEquals(1, fences.size());
        staging.flush(commands);
        Mockito.verify(commands, Mockito.times(2)).createFence();
        reclaim(staging);
        Mockito.clearInvocations(commands);
        staging.enqueueCopy(commands, data(32, 0), dst, 0);
        Mockito.verifyNoInteractions(commands);
    }

    // Checks that every flush fences its own region and that flip reclaims them in submission order
    @Test
    void flushFencesEachBatchAsItsOwnRegion() {
        MappedStagingBuffer staging = staging(32);
        staging.enqueueCopy(commands, data(16, 0), dst, 0);
        staging.flush(commands);
        staging.enqueueCopy(commands, data(16, 0), dst, 16);
        staging.flush(commands);
        assertEquals(2, fences.size());
        Mockito.when(fences.get(1).isCompleted()).thenReturn(true);
        staging.flip();
        Mockito.verify(fences.get(1), Mockito.never()).isCompleted();
        Mockito.clearInvocations(commands);
        ByteBuffer one = data(1, 0);
        staging.enqueueCopy(commands, one, dst, 32);
        verifyFallback(one, dst, 32, 1);
        Mockito.when(fences.get(0).isCompleted()).thenReturn(true);
        staging.flip();
        Mockito.verify(fences.get(0)).delete();
        Mockito.verify(fences.get(1)).delete();
        Mockito.clearInvocations(commands);
        staging.enqueueCopy(commands, data(32, 0), dst, 0);
        Mockito.verifyNoInteractions(commands);
    }

    // Checks that flip without fences makes no call before and after delete
    @Test
    void flipWithoutFencesMakesNoCall() {
        MappedStagingBuffer staging = staging(32);
        staging.flip();
        staging.flip();
        staging.enqueueCopy(commands, data(8, 0), dst, 0);
        staging.flip();
        Mockito.verifyNoInteractions(commands);
        staging.delete(commands);
        Mockito.clearInvocations(commands);
        staging.flip();
        Mockito.verifyNoInteractions(commands);
    }

    // Checks that flip polls an unsignalled fence without freeing it and frees a signalled fence once
    @Test
    void flipDeletesEachSignalledFenceOnce() {
        MappedStagingBuffer staging = staging(32);
        staging.enqueueCopy(commands, data(16, 0), dst, 0);
        staging.flush(commands);
        GlFence fence = fences.get(0);
        staging.flip();
        Mockito.verify(fence).isCompleted();
        Mockito.verify(fence, Mockito.never()).delete();
        Mockito.when(fence.isCompleted()).thenReturn(true);
        staging.flip();
        staging.flip();
        staging.flip();
        Mockito.verify(fence, Mockito.times(2)).isCompleted();
        Mockito.verify(fence).delete();
        assertEquals("Mapped (0/0 MiB)", staging.toString());
    }

    // Checks that flip reclaims every leading signalled region and keeps the regions from the first unsignalled one
    @Test
    void flipReclaimsEveryLeadingSignalledRegion() {
        MappedStagingBuffer staging = staging(30);
        for (int i = 0; i < 3; i++) {
            staging.enqueueCopy(commands, data(10, 0), dst, 10L * i);
            staging.flush(commands);
        }
        Mockito.when(fences.get(0).isCompleted()).thenReturn(true);
        Mockito.when(fences.get(1).isCompleted()).thenReturn(true);
        staging.flip();
        Mockito.verify(fences.get(0)).delete();
        Mockito.verify(fences.get(1)).delete();
        Mockito.verify(fences.get(2)).isCompleted();
        Mockito.verify(fences.get(2), Mockito.never()).delete();
        Mockito.clearInvocations(commands);
        staging.enqueueCopy(commands, data(20, 0), dst, 0);
        Mockito.verifyNoInteractions(commands);
        ByteBuffer one = data(1, 0);
        staging.enqueueCopy(commands, one, dst, 20);
        verifyFallback(one, dst, 20, 1);
    }

    // Checks that a failed fence poll propagates and keeps the region for the next flip
    @Test
    void flipKeepsTheRegionWhenAPollFails() {
        MappedStagingBuffer staging = staging(32);
        staging.enqueueCopy(commands, data(32, 0), dst, 0);
        staging.flush(commands);
        GlFence fence = fences.get(0);
        IllegalStateException failure = new IllegalStateException("poll");
        Mockito.when(fence.isCompleted()).thenThrow(failure).thenReturn(true);
        assertSame(failure, assertThrows(IllegalStateException.class, staging::flip));
        staging.flip();
        Mockito.verify(fence).delete();
        Mockito.clearInvocations(commands);
        staging.enqueueCopy(commands, data(32, 0), dst, 0);
        Mockito.verifyNoInteractions(commands);
    }

    // Checks that delete unmaps and frees the ring and the scratch buffer and every fence and drops the staged copies
    @Test
    void deleteFreesTheRingTheFallbackAndEveryFence() {
        MappedStagingBuffer staging = staging(32);
        staging.enqueueCopy(commands, data(8, 0), dst, 0);
        staging.flush(commands);
        staging.enqueueCopy(commands, data(8, 0), dst, 8);
        staging.flush(commands);
        staging.enqueueCopy(commands, data(8, 0), dst, 16);
        Mockito.clearInvocations(commands);
        staging.delete(commands);
        InOrder order = Mockito.inOrder(commands, fences.get(0), fences.get(1));
        order.verify(commands).unmap(same(mapping));
        order.verify(commands).deleteBuffer(same(ring));
        order.verify(commands).deleteBuffer(same(scratch));
        order.verify(fences.get(0)).delete();
        order.verify(fences.get(1)).delete();
        Mockito.verifyNoMoreInteractions(commands);
        Mockito.verify(fences.get(0), Mockito.never()).isCompleted();
    }

    // Checks that repeated deletes free everything once
    @Test
    void deleteFreesOnceOnRepeatedCalls() {
        MappedStagingBuffer staging = staging(32);
        staging.enqueueCopy(commands, data(8, 0), dst, 0);
        staging.flush(commands);
        Mockito.clearInvocations(commands);
        staging.delete(commands);
        staging.delete(commands);
        staging.delete(commands);
        Mockito.verify(commands).unmap(same(mapping));
        Mockito.verify(commands).deleteBuffer(same(ring));
        Mockito.verify(commands).deleteBuffer(same(scratch));
        Mockito.verify(fences.get(0)).delete();
        Mockito.verifyNoMoreInteractions(commands);
    }

    // Checks that delete rejects a null command list before and after the ring is freed
    @Test
    void deleteRejectsNullCommandList() {
        MappedStagingBuffer staging = staging(32);
        assertThrows(NullPointerException.class, () -> staging.delete(null));
        Mockito.verifyNoInteractions(commands);
        staging.enqueueCopy(commands, data(8, 0), dst, 0);
        staging.flush(commands);
        verifyRingCopy(dst, 0, 0, 8);
        staging.delete(commands);
        assertThrows(NullPointerException.class, () -> staging.delete(null));
        Mockito.verify(commands).unmap(same(mapping));
    }

    // Checks that a failed unmap propagates and that the ring rejects later use and frees nothing again
    @Test
    void deleteFailureStillRejectsLaterUse() {
        MappedStagingBuffer staging = staging(32);
        IllegalStateException failure = new IllegalStateException("unmap");
        Mockito.doThrow(failure).when(commands).unmap(any());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> staging.delete(commands)));
        Mockito.clearInvocations(commands);
        assertThrows(IllegalStateException.class, () -> staging.enqueueCopy(commands, data(8, 0), dst, 0));
        assertThrows(IllegalStateException.class, () -> staging.flush(commands));
        staging.delete(commands);
        Mockito.verifyNoInteractions(commands);
        assertRingUntouched();
    }

    // Checks the free and total ring size in MiB as copies are staged and reclaimed and after delete
    @Test
    void toStringReportsTheFreeAndTotalRingSizeInMiB() {
        MappedStagingBuffer staging = staging(3 * MIB);
        assertEquals("Mapped (3/3 MiB)", staging.toString());
        staging.enqueueCopy(commands, alloc(MIB + 1), dst, 0);
        assertEquals("Mapped (1/3 MiB)", staging.toString());
        staging.flush(commands);
        assertEquals("Mapped (1/3 MiB)", staging.toString());
        staging.enqueueCopy(commands, alloc(2 * MIB), dst, 0);
        assertEquals("Mapped (1/3 MiB)", staging.toString());
        reclaim(staging);
        assertEquals("Mapped (3/3 MiB)", staging.toString());
        assertEquals(staging.toString(), staging.toString());
        staging.delete(commands);
        assertEquals("Mapped (3/3 MiB)", staging.toString());
    }

    // Checks the ring data and the flush and copy and fence calls on the test render device
    @Test
    void enqueueCopyAndFlushOnTheDevice() {
        onDevice(list -> {
            MappedStagingBuffer staging = new MappedStagingBuffer(list, 64);
            ArgumentCaptor<GlImmutableBuffer> created = ArgumentCaptor.forClass(GlImmutableBuffer.class);
            Mockito.verify(list).mapBuffer(created.capture(), eq(0L), eq(64L), any());
            GlBufferMapping deviceMapping = created.getValue().getActiveMapping();
            GlMutableBuffer target = list.createMutableBuffer();
            ByteBuffer data = data(24, 3);
            data.position(4);
            staging.enqueueCopy(list, data, target, 40);
            for (int i = 0; i < 20; i++) {
                assertEquals(data.get(4 + i), deviceMapping.getMemoryBuffer().get(i));
            }
            staging.flush(list);
            Mockito.verify(TestGl.gl()).glBufferStorage(GlBufferTarget.ARRAY_BUFFER.getTargetParameter(), 64L, STORAGE_BITS);
            Mockito.verify(TestGl.gl()).glFlushMappedBufferRange(GlBufferTarget.COPY_READ_BUFFER.getTargetParameter(), 0L, 20L);
            Mockito.verify(TestGl.gl()).glCopyBufferSubData(GlBufferTarget.COPY_READ_BUFFER.getTargetParameter(), GlBufferTarget.COPY_WRITE_BUFFER.getTargetParameter(), 0L, 40L, 20L);
            Mockito.verify(TestGl.gl()).glFenceSync(GL32.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            staging.flip();
            Mockito.verify(TestGl.gl()).glDeleteSync(anyLong());
            list.deleteBuffer(target);
        });
    }

    // Checks that delete unmaps and frees the ring once and that later use is rejected on the test render device
    @Test
    void deleteAndUseAfterDeleteOnTheDevice() {
        onDevice(list -> {
            MappedStagingBuffer staging = new MappedStagingBuffer(list, 64);
            ArgumentCaptor<GlImmutableBuffer> created = ArgumentCaptor.forClass(GlImmutableBuffer.class);
            Mockito.verify(list).mapBuffer(created.capture(), eq(0L), eq(64L), any());
            GlImmutableBuffer deviceRing = created.getValue();
            GlBufferMapping deviceMapping = deviceRing.getActiveMapping();
            int name = deviceRing.handle();
            GlMutableBuffer target = list.createMutableBuffer();
            ByteBuffer data = data(8, 0);
            staging.enqueueCopy(list, data, target, 0);
            staging.flush(list);
            staging.delete(list);
            staging.delete(list);
            assertTrue(deviceMapping.isDisposed());
            Mockito.verify(TestGl.gl()).glUnmapBuffer(GlBufferTarget.ARRAY_BUFFER.getTargetParameter());
            Mockito.verify(TestGl.gl()).glDeleteBuffers(name);
            Mockito.verify(TestGl.gl()).glDeleteSync(anyLong());
            assertThrows(IllegalStateException.class, deviceRing::handle);
            assertThrows(IllegalStateException.class, () -> staging.enqueueCopy(list, data, target, 0));
            assertThrows(IllegalStateException.class, () -> staging.flush(list));
            list.deleteBuffer(target);
        });
    }
}
