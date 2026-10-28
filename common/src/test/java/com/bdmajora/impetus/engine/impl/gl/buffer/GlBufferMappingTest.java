package com.bdmajora.impetus.engine.impl.gl.buffer;

import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.system.MemoryUtil;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests of every member of the buffer mapping over guarded native memory
class GlBufferMappingTest {
    // Message of a null buffer
    private static final String NO_BUFFER = "Buffer must not be null";
    // Message of a null memory buffer
    private static final String NO_MEMORY = "Memory buffer must not be null";
    // Message of null data
    private static final String NO_DATA = "Data must not be null";
    // Message of a write after dispose
    private static final String DISPOSED = "Buffer mapping is already disposed";
    // Number of guard bytes on each side of a guarded range
    private static final int GUARD = 32;
    // Value of every guard byte
    private static final byte GUARD_BYTE = (byte) 0xA5;
    // Value of the byte past the limit of every data block
    private static final byte SENTINEL = (byte) 0x5A;
    // Capacity of the memory in the exhaustive sweep
    private static final int SWEEP = 24;
    // Map flags of a plain write mapping
    private static final EnumBitField<GlBufferMapFlags> WRITE = EnumBitField.of(GlBufferMapFlags.WRITE);

    // Shared GL mock that the reset extension clears before each test
    private final LWJGLService gl = TestGl.gl();
    // Guarded native blocks that the test checks and frees after it ends
    private final List<Guarded> guarded = new ArrayList<>();
    // Plain native blocks that the test frees after it ends
    private final List<ByteBuffer> blocks = new ArrayList<>();

    // Native block with guard bytes around a range of the given length
    private record Guarded(ByteBuffer whole, int length) {
    }

    // Mapping that reads and clears the protected disposal flag
    private static final class Reopenable extends GlBufferMapping {
        // Creates the mapping of the buffer over the memory
        Reopenable(GlBuffer buffer, ByteBuffer map) {
            super(buffer, map);
        }

        // Returns the protected disposal flag
        boolean flag() {
            return this.disposed;
        }

        // Clears the protected disposal flag
        void reopen() {
            this.disposed = false;
        }
    }

    // Checks every guard byte and frees every native block of the test
    @AfterEach
    void freeBlocks() {
        List<String> damaged = new ArrayList<>();
        try {
            for (Guarded block : guarded) {
                long base = MemoryUtil.memAddress(block.whole());
                long after = base + GUARD + block.length();
                for (int i = 0; i < GUARD; i++) {
                    if (MemoryUtil.memGetByte(base + i) != GUARD_BYTE) {
                        damaged.add("guard byte " + i + " before a range of " + block.length() + " bytes");
                    }
                    if (MemoryUtil.memGetByte(after + i) != GUARD_BYTE) {
                        damaged.add("guard byte " + i + " after a range of " + block.length() + " bytes");
                    }
                }
            }
        } finally {
            for (Guarded block : guarded) {
                MemoryUtil.memFree(block.whole());
            }
            for (ByteBuffer block : blocks) {
                MemoryUtil.memFree(block);
            }
            guarded.clear();
            blocks.clear();
        }
        assertEquals(List.of(), damaged);
    }

    // Returns a range of zeroed native memory between two runs of guard bytes
    private ByteBuffer guarded(int length) {
        ByteBuffer whole = MemoryUtil.memAlloc(length + 2 * GUARD);
        guarded.add(new Guarded(whole, length));
        long base = MemoryUtil.memAddress(whole);
        MemoryUtil.memSet(base, GUARD_BYTE, whole.capacity());
        MemoryUtil.memSet(base + GUARD, 0, length);
        return MemoryUtil.memByteBuffer(base + GUARD, length);
    }

    // Returns direct data of the given length filled from the seed with a sentinel byte past its limit
    private ByteBuffer data(int length, int seed) {
        ByteBuffer block = MemoryUtil.memAlloc(length + 1);
        blocks.add(block);
        for (int i = 0; i < length; i++) {
            block.put(i, pattern(seed, i));
        }
        block.put(length, SENTINEL);
        return block.limit(length);
    }

    // Returns the pattern byte of the seed at the index
    private static byte pattern(int seed, int index) {
        return (byte) (seed + index * 37);
    }

    // Returns every byte of the buffer up to its capacity whatever its position and limit
    private static byte[] bytes(ByteBuffer buffer) {
        byte[] out = new byte[buffer.capacity()];
        buffer.duplicate().clear().get(0, out);
        return out;
    }

    // Fills the buffer up to its capacity from the seed whatever its position and limit
    private static void fill(ByteBuffer buffer, int seed) {
        ByteBuffer view = buffer.duplicate().clear();
        for (int i = 0; i < view.capacity(); i++) {
            view.put(i, pattern(seed, i));
        }
    }

    // Returns a copy of the bytes with the source range copied to the offset
    private static byte[] copied(byte[] before, byte[] source, int from, int offset, int length) {
        byte[] out = before.clone();
        System.arraycopy(source, from, out, offset, length);
        return out;
    }

    // Creates a mapping of a fresh buffer over the memory
    private static GlBufferMapping over(ByteBuffer memory) {
        return new GlBufferMapping(new GlMutableBuffer(), memory);
    }

    // Runs the body with a command list of an active render device
    private static void onDevice(Consumer<CommandList> body) {
        GLRenderDevice device = new GLRenderDevice();
        device.makeActive();
        try {
            body.accept(device.createCommandList());
        } finally {
            device.makeInactive();
        }
    }

    // Asserts that the method is public with the given return type
    private static void assertPublic(Method method, Class<?> returns) {
        assertEquals(Modifier.PUBLIC, method.getModifiers(), method.getName());
        assertSame(returns, method.getReturnType(), method.getName());
    }

    // Stores the buffer and the memory buffer and starts undisposed
    @Test
    void constructorStoresTheBufferAndTheMemory() {
        GlBuffer buffer = new GlMutableBuffer();
        ByteBuffer memory = guarded(16);
        GlBufferMapping mapping = new GlBufferMapping(buffer, memory);
        assertSame(buffer, mapping.getBufferObject());
        assertSame(memory, mapping.getMemoryBuffer());
        assertFalse(mapping.isDisposed());
    }

    // Rejects a null buffer
    @Test
    void constructorRejectsANullBuffer() {
        ByteBuffer memory = guarded(4);
        assertEquals(NO_BUFFER, assertThrows(NullPointerException.class, () -> new GlBufferMapping(null, memory)).getMessage());
    }

    // Rejects a null memory buffer
    @Test
    void constructorRejectsANullMemoryBuffer() {
        GlBuffer buffer = new GlMutableBuffer();
        assertEquals(NO_MEMORY, assertThrows(NullPointerException.class, () -> new GlBufferMapping(buffer, null)).getMessage());
    }

    // Rejects a null buffer before a null memory buffer
    @Test
    void constructorRejectsANullBufferFirst() {
        assertEquals(NO_BUFFER, assertThrows(NullPointerException.class, () -> new GlBufferMapping(null, null)).getMessage());
    }

    // Leaves the position and limit and mark and order and contents of the memory untouched
    @Test
    void constructorLeavesTheMemoryUntouched() {
        ByteBuffer memory = guarded(16).order(ByteOrder.BIG_ENDIAN);
        fill(memory, 3);
        byte[] before = bytes(memory);
        memory.position(2).mark();
        memory.position(5).limit(9);
        new GlBufferMapping(new GlMutableBuffer(), memory);
        assertEquals(5, memory.position());
        assertEquals(9, memory.limit());
        assertEquals(16, memory.capacity());
        assertSame(ByteOrder.BIG_ENDIAN, memory.order());
        assertEquals(2, memory.reset().position());
        assertArrayEquals(before, bytes(memory));
    }

    // Makes no GL call and leaves the buffer without an active mapping
    @Test
    void constructorMakesNoGlCallAndRegistersNothing() {
        GlBuffer buffer = new GlMutableBuffer();
        ByteBuffer memory = guarded(8);
        Mockito.clearInvocations(gl);
        new GlBufferMapping(buffer, memory);
        Mockito.verifyNoInteractions(gl);
        assertNull(buffer.getActiveMapping());
    }

    // Accepts empty and heap and read only memory and a deleted buffer
    @Test
    void constructorAcceptsEveryKindOfMemoryAndADeletedBuffer() {
        for (ByteBuffer memory : List.of(guarded(0), ByteBuffer.allocate(8), guarded(8).asReadOnlyBuffer(), ByteBuffer.allocateDirect(4))) {
            GlBufferMapping mapping = over(memory);
            assertSame(memory, mapping.getMemoryBuffer());
            assertFalse(mapping.isDisposed());
        }
        GlBuffer deleted = new GlMutableBuffer();
        deleted.delete();
        GlBufferMapping mapping = new GlBufferMapping(deleted, guarded(4));
        assertSame(deleted, mapping.getBufferObject());
        assertFalse(mapping.isDisposed());
    }

    // Copies every range from every source alignment and leaves every other byte untouched
    @Test
    void writeMatchesAReferenceCopyForEveryRangeAndAlignment() {
        ByteBuffer memory = guarded(SWEEP);
        GlBufferMapping mapping = over(memory);
        ByteBuffer source = data(SWEEP + 8, 11);
        byte[] sourceBytes = bytes(source);
        for (int shift = 0; shift < 8; shift++) {
            for (int offset = 0; offset <= SWEEP; offset++) {
                for (int length = 0; length <= SWEEP - offset; length++) {
                    fill(memory, ~offset);
                    byte[] before = bytes(memory);
                    source.limit(shift + length).position(shift);
                    mapping.write(source, offset);
                    int s = shift;
                    int o = offset;
                    int l = length;
                    assertArrayEquals(copied(before, sourceBytes, shift, offset, length), bytes(memory), () -> "shift " + s + " offset " + o + " length " + l);
                    assertEquals(shift, source.position());
                    assertEquals(shift + length, source.limit());
                }
            }
        }
    }

    // Copies large blocks to aligned and misaligned offsets
    @ParameterizedTest
    @ValueSource(ints = {159, 160, 161, 4095, 4096, 4097, 1 << 20})
    void writeCopiesLargeBlocks(int length) {
        for (int offset : new int[] {0, 1, 7}) {
            ByteBuffer memory = guarded(length + 8);
            ByteBuffer source = data(length, length + offset);
            byte[] before = bytes(memory);
            over(memory).write(source, offset);
            assertArrayEquals(copied(before, bytes(source), 0, offset, length), bytes(memory));
        }
    }

    // Leaves the position and limit and mark and order and contents of the data and the memory untouched
    @Test
    void writeLeavesEveryBufferStateUntouched() {
        ByteBuffer memory = guarded(16).order(ByteOrder.LITTLE_ENDIAN);
        GlBufferMapping mapping = over(memory);
        memory.position(3).mark();
        memory.position(4).limit(12);
        ByteBuffer source = data(12, 5).order(ByteOrder.BIG_ENDIAN);
        source.position(1).mark();
        source.position(2).limit(10);
        byte[] sourceBefore = bytes(source);
        mapping.write(source, 6);
        assertEquals(4, memory.position());
        assertEquals(12, memory.limit());
        assertSame(ByteOrder.LITTLE_ENDIAN, memory.order());
        assertEquals(3, memory.reset().position());
        assertEquals(2, source.position());
        assertEquals(10, source.limit());
        assertSame(ByteOrder.BIG_ENDIAN, source.order());
        assertEquals(1, source.reset().position());
        assertArrayEquals(sourceBefore, bytes(source));
    }

    // Writes from the start of the memory up to its capacity whatever its position and limit
    @Test
    void writeIgnoresThePositionAndLimitOfTheMemory() {
        ByteBuffer memory = guarded(16);
        GlBufferMapping mapping = over(memory);
        memory.position(6).limit(8);
        ByteBuffer whole = data(16, 9);
        mapping.write(whole, 0);
        byte[] expected = copied(new byte[16], bytes(whole), 0, 0, 16);
        assertArrayEquals(expected, bytes(memory));
        memory.limit(0);
        ByteBuffer tail = data(4, 2);
        mapping.write(tail, 12);
        assertArrayEquals(copied(expected, bytes(tail), 0, 12, 4), bytes(memory));
    }

    // Fills the memory exactly up to its capacity
    @Test
    void writeFillsTheMemoryExactly() {
        ByteBuffer memory = guarded(16);
        GlBufferMapping mapping = over(memory);
        ByteBuffer whole = data(16, 1);
        mapping.write(whole, 0);
        byte[] expected = copied(new byte[16], bytes(whole), 0, 0, 16);
        assertArrayEquals(expected, bytes(memory));
        mapping.write(data(1, 99), 15);
        expected[15] = pattern(99, 0);
        assertArrayEquals(expected, bytes(memory));
        mapping.write(data(0, 0), 16);
        assertArrayEquals(expected, bytes(memory));
    }

    // Rejects a range past the capacity and leaves the memory and the data untouched
    @ParameterizedTest
    @CsvSource({"16, 1", "17, 0", "15, 2", "0, 17", "1, 16", "8, 9", "16, 16"})
    void writeRejectsARangePastTheCapacity(int offset, int length) {
        ByteBuffer memory = guarded(16);
        fill(memory, 4);
        byte[] before = bytes(memory);
        GlBufferMapping mapping = over(memory);
        ByteBuffer source = data(length, 6);
        assertThrows(IndexOutOfBoundsException.class, () -> mapping.write(source, offset));
        assertArrayEquals(before, bytes(memory));
        assertEquals(0, source.position());
        assertEquals(length, source.limit());
    }

    // Rejects a negative offset whatever the length and leaves the memory untouched
    @ParameterizedTest
    @ValueSource(ints = {-1, -2, -8, -4096, Integer.MIN_VALUE + 1, Integer.MIN_VALUE})
    void writeRejectsANegativeOffset(int offset) {
        ByteBuffer memory = guarded(16);
        fill(memory, 8);
        byte[] before = bytes(memory);
        GlBufferMapping mapping = over(memory);
        for (int length : new int[] {0, 1, 16}) {
            ByteBuffer source = data(length, length);
            assertThrows(IndexOutOfBoundsException.class, () -> mapping.write(source, offset));
        }
        assertArrayEquals(before, bytes(memory));
    }

    // Rejects a range end past the int limit before any memory is touched
    @Test
    void writeRejectsAnEndPastTheIntLimit() {
        ByteBuffer tiny = MemoryUtil.memAlloc(1);
        blocks.add(tiny);
        ByteBuffer huge = MemoryUtil.memByteBuffer(MemoryUtil.memAddress(tiny), Integer.MAX_VALUE);
        GlBufferMapping mapping = over(huge);
        assertThrows(IndexOutOfBoundsException.class, () -> mapping.write(data(1, 0), Integer.MAX_VALUE));
        assertThrows(IndexOutOfBoundsException.class, () -> mapping.write(data(2, 0), Integer.MAX_VALUE - 1));
        assertThrows(IndexOutOfBoundsException.class, () -> mapping.write(huge.duplicate(), 1));
        mapping.write(data(0, 0), Integer.MAX_VALUE);
        GlBufferMapping small = over(guarded(16));
        assertThrows(IndexOutOfBoundsException.class, () -> small.write(huge.duplicate(), 0));
        assertThrows(IndexOutOfBoundsException.class, () -> small.write(data(1, 0), Integer.MAX_VALUE));
    }

    // Rejects null data at any offset and leaves the memory untouched
    @Test
    void writeRejectsNullData() {
        ByteBuffer memory = guarded(8);
        fill(memory, 2);
        byte[] before = bytes(memory);
        GlBufferMapping mapping = over(memory);
        assertEquals(NO_DATA, assertThrows(NullPointerException.class, () -> mapping.write(null, 0)).getMessage());
        assertEquals(NO_DATA, assertThrows(NullPointerException.class, () -> mapping.write(null, -1)).getMessage());
        assertArrayEquals(before, bytes(memory));
    }

    // Copies nothing for empty data at every offset up to the capacity
    @Test
    void writeCopiesNothingForEmptyData() {
        ByteBuffer memory = guarded(8);
        fill(memory, 6);
        byte[] before = bytes(memory);
        GlBufferMapping mapping = over(memory);
        ByteBuffer drained = data(8, 1);
        drained.position(8);
        for (int offset = 0; offset <= 8; offset++) {
            mapping.write(drained, offset);
            mapping.write(data(0, 0), offset);
            mapping.write(ByteBuffer.allocateDirect(0), offset);
            mapping.write(ByteBuffer.allocate(0), offset);
        }
        assertArrayEquals(before, bytes(memory));
        assertEquals(8, drained.position());
    }

    // Accepts only empty data at offset zero into empty memory
    @Test
    void writeIntoEmptyMemoryAcceptsOnlyEmptyDataAtZero() {
        GlBufferMapping mapping = over(guarded(0));
        mapping.write(data(0, 0), 0);
        assertThrows(IndexOutOfBoundsException.class, () -> mapping.write(data(1, 0), 0));
        assertThrows(IndexOutOfBoundsException.class, () -> mapping.write(data(0, 0), 1));
    }

    // Copies the remaining bytes of heap data with an array offset
    @Test
    void writeCopiesFromHeapData() {
        byte[] array = new byte[24];
        for (int i = 0; i < array.length; i++) {
            array[i] = pattern(13, i);
        }
        ByteBuffer source = ByteBuffer.wrap(array).position(3).slice();
        source.position(2).limit(12);
        ByteBuffer memory = guarded(16);
        byte[] before = bytes(memory);
        over(memory).write(source, 5);
        assertArrayEquals(copied(before, array, 5, 5, 10), bytes(memory));
        assertEquals(2, source.position());
        assertEquals(12, source.limit());
    }

    // Copies the remaining bytes of a read only direct view
    @Test
    void writeCopiesFromReadOnlyDirectData() {
        ByteBuffer data = data(12, 21);
        ByteBuffer view = data.asReadOnlyBuffer();
        view.position(4);
        ByteBuffer memory = guarded(16);
        byte[] before = bytes(memory);
        over(memory).write(view, 0);
        assertArrayEquals(copied(before, bytes(data), 4, 0, 8), bytes(memory));
        assertEquals(4, view.position());
    }

    // Copies the remaining bytes of a read only heap view
    @Test
    void writeCopiesFromReadOnlyHeapData() {
        byte[] array = {9, 8, 7, 6, 5};
        ByteBuffer view = ByteBuffer.wrap(array).asReadOnlyBuffer();
        view.position(1);
        ByteBuffer memory = guarded(8);
        byte[] before = bytes(memory);
        over(memory).write(view, 3);
        assertArrayEquals(copied(before, array, 1, 3, 4), bytes(memory));
        assertEquals(1, view.position());
    }

    // Copies direct and heap data into heap memory with an array offset
    @Test
    void writeCopiesIntoHeapMemory() {
        byte[] array = new byte[20];
        ByteBuffer memory = ByteBuffer.wrap(array).position(4).slice();
        GlBufferMapping mapping = over(memory);
        ByteBuffer direct = data(6, 31);
        mapping.write(direct, 2);
        byte[] expected = copied(new byte[20], bytes(direct), 0, 6, 6);
        assertArrayEquals(expected, array);
        ByteBuffer heap = ByteBuffer.wrap(new byte[] {1, 2, 3});
        mapping.write(heap, 13);
        expected = copied(expected, new byte[] {1, 2, 3}, 0, 17, 3);
        assertArrayEquals(expected, array);
        assertThrows(IndexOutOfBoundsException.class, () -> mapping.write(heap, 14));
        assertArrayEquals(expected, array);
    }

    // Rejects every write into a read only view of the memory and leaves the memory untouched
    @Test
    void writeRejectsReadOnlyMemory() {
        ByteBuffer memory = guarded(16);
        fill(memory, 17);
        byte[] before = bytes(memory);
        GlBufferMapping mapping = over(memory.asReadOnlyBuffer());
        assertThrows(ReadOnlyBufferException.class, () -> mapping.write(data(4, 1), 0));
        assertThrows(ReadOnlyBufferException.class, () -> mapping.write(data(0, 1), 0));
        assertThrows(IndexOutOfBoundsException.class, () -> mapping.write(data(4, 1), 13));
        assertArrayEquals(before, bytes(memory));
    }

    // Copies an overlapping range of the memory as if through a temporary copy
    @ParameterizedTest
    @CsvSource({"0, 1, 4", "0, 1, 8", "0, 1, 63", "1, 0, 63", "0, 8, 32", "8, 0, 32", "0, 3, 61", "3, 0, 61", "5, 5, 10", "0, 32, 32", "2, 4, 2"})
    void writeCopiesAnOverlappingRangeLikeAMove(int from, int offset, int length) {
        ByteBuffer memory = guarded(64);
        fill(memory, 27);
        byte[] before = bytes(memory);
        GlBufferMapping mapping = over(memory);
        ByteBuffer view = memory.duplicate();
        view.limit(from + length).position(from);
        mapping.write(view, offset);
        assertArrayEquals(copied(before, before, from, offset, length), bytes(memory));
    }

    // Copies an overlapping range of heap memory as if through a temporary copy
    @Test
    void writeCopiesAnOverlappingRangeOfHeapMemory() {
        ByteBuffer memory = ByteBuffer.allocate(32);
        fill(memory, 19);
        byte[] before = bytes(memory);
        GlBufferMapping mapping = over(memory);
        ByteBuffer view = memory.duplicate();
        view.limit(20);
        mapping.write(view, 1);
        assertArrayEquals(copied(before, before, 0, 1, 20), bytes(memory));
    }

    // Accepts the memory buffer itself as data and copies its remaining bytes to the offset
    @Test
    void writeAcceptsTheMemoryBufferItselfAsData() {
        ByteBuffer memory = guarded(16);
        fill(memory, 41);
        byte[] before = bytes(memory);
        GlBufferMapping mapping = over(memory);
        mapping.write(memory, 0);
        assertArrayEquals(before, bytes(memory));
        memory.position(4);
        mapping.write(memory, 0);
        byte[] moved = copied(before, before, 4, 0, 12);
        assertArrayEquals(moved, bytes(memory));
        assertEquals(4, memory.position());
        assertThrows(IndexOutOfBoundsException.class, () -> mapping.write(memory, 5));
        assertArrayEquals(moved, bytes(memory));
    }

    // Rejects every write after dispose before the range check and leaves the memory untouched
    @Test
    void writeAfterDisposeIsRefused() {
        ByteBuffer memory = guarded(16);
        fill(memory, 12);
        byte[] before = bytes(memory);
        GlBufferMapping mapping = over(memory);
        mapping.dispose();
        assertEquals(DISPOSED, assertThrows(IllegalStateException.class, () -> mapping.write(data(4, 0), 0)).getMessage());
        assertEquals(DISPOSED, assertThrows(IllegalStateException.class, () -> mapping.write(data(0, 0), 0)).getMessage());
        assertEquals(DISPOSED, assertThrows(IllegalStateException.class, () -> mapping.write(data(4, 0), -1)).getMessage());
        assertEquals(DISPOSED, assertThrows(IllegalStateException.class, () -> mapping.write(data(4, 0), 16)).getMessage());
        assertEquals(NO_DATA, assertThrows(NullPointerException.class, () -> mapping.write(null, 0)).getMessage());
        assertArrayEquals(before, bytes(memory));
    }

    // Rejects a write after the buffer deletes its open mapping
    @Test
    void writeAfterTheBufferIsDeletedIsRefused() {
        GlBuffer buffer = new GlMutableBuffer();
        ByteBuffer memory = guarded(8);
        GlBufferMapping mapping = new GlBufferMapping(buffer, memory);
        buffer.setActiveMapping(mapping);
        ByteBuffer source = data(8, 3);
        mapping.write(source, 0);
        byte[] written = bytes(memory);
        buffer.delete();
        assertEquals(DISPOSED, assertThrows(IllegalStateException.class, () -> mapping.write(data(8, 4), 0)).getMessage());
        assertArrayEquals(written, bytes(memory));
    }

    // Writes through a mapping of a real command list and rejects the write after unmap
    @Test
    void writeThroughADeviceMappingIsRefusedAfterUnmap() {
        onDevice(list -> {
            GlMutableBuffer buffer = list.createMutableBuffer();
            GlBufferMapping mapping = list.mapBuffer(buffer, 0L, 8L, WRITE);
            ByteBuffer source = data(8, 77);
            mapping.write(source, 0);
            assertArrayEquals(copied(new byte[8], bytes(source), 0, 0, 8), bytes(mapping.getMemoryBuffer()));
            list.unmap(mapping);
            assertEquals(DISPOSED, assertThrows(IllegalStateException.class, () -> mapping.write(source, 0)).getMessage());
            list.deleteBuffer(buffer);
        });
    }

    // Keeps the last of overlapping writes and lands every one of many small writes
    @Test
    void writeRepeatedlyKeepsTheLastBytes() {
        ByteBuffer memory = guarded(16);
        GlBufferMapping mapping = over(memory);
        ByteBuffer first = data(8, 1);
        ByteBuffer second = data(8, 2);
        mapping.write(first, 0);
        mapping.write(second, 4);
        byte[] expected = copied(copied(new byte[16], bytes(first), 0, 0, 8), bytes(second), 0, 4, 8);
        assertArrayEquals(expected, bytes(memory));
        ByteBuffer one = data(1, 0);
        for (int i = 0; i < 1000; i++) {
            one.put(0, (byte) i);
            mapping.write(one, i % 16);
            expected[i % 16] = (byte) i;
        }
        assertArrayEquals(expected, bytes(memory));
    }

    // Copies raw bytes whatever the byte order of the data and the memory
    @Test
    void writeCopiesRawBytesWhateverTheByteOrder() {
        ByteBuffer memory = guarded(8).order(ByteOrder.LITTLE_ENDIAN);
        GlBufferMapping mapping = over(memory);
        ByteBuffer source = data(4, 0).order(ByteOrder.BIG_ENDIAN);
        source.putInt(0, 0x01020304);
        mapping.write(source, 2);
        assertArrayEquals(new byte[] {0, 0, 1, 2, 3, 4, 0, 0}, bytes(memory));
        assertEquals(0x04030201, memory.getInt(2));
    }

    // Lands every disjoint range written from several threads at once
    @Test
    void writeFromSeveralThreadsLandsEveryRange() throws InterruptedException {
        int threads = 8;
        int span = 512;
        ByteBuffer memory = guarded(threads * span);
        GlBufferMapping mapping = over(memory);
        ByteBuffer[] sources = new ByteBuffer[threads];
        Thread[] workers = new Thread[threads];
        AtomicReference<Throwable> failure = new AtomicReference<>();
        for (int t = 0; t < threads; t++) {
            sources[t] = data(span, t * 3 + 1);
            int index = t;
            workers[t] = new Thread(() -> {
                try {
                    for (int round = 0; round < 200; round++) {
                        mapping.write(sources[index], index * span);
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            });
        }
        for (Thread worker : workers) {
            worker.start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        assertNull(failure.get());
        byte[] expected = new byte[threads * span];
        for (int t = 0; t < threads; t++) {
            System.arraycopy(bytes(sources[t]), 0, expected, t * span, span);
        }
        assertArrayEquals(expected, bytes(memory));
    }

    // Returns the same buffer on every call before and after dispose and after the buffer is deleted
    @Test
    void getBufferObjectReturnsTheSameBufferEveryTime() {
        GlBuffer buffer = new GlMutableBuffer();
        GlBufferMapping mapping = new GlBufferMapping(buffer, guarded(4));
        assertSame(buffer, mapping.getBufferObject());
        assertSame(buffer, mapping.getBufferObject());
        mapping.dispose();
        assertSame(buffer, mapping.getBufferObject());
        buffer.delete();
        assertSame(buffer, mapping.getBufferObject());
    }

    // Marks the mapping disposed and keeps it disposed on repeated calls
    @Test
    void disposeMarksTheMappingDisposedOnRepeatedCalls() {
        GlBufferMapping mapping = over(guarded(4));
        mapping.dispose();
        assertTrue(mapping.isDisposed());
        mapping.dispose();
        assertTrue(mapping.isDisposed());
    }

    // Leaves the memory and its state and the buffer and the GL mock untouched
    @Test
    void disposeTouchesNothingElse() {
        GlBuffer buffer = new GlMutableBuffer();
        ByteBuffer memory = guarded(16);
        fill(memory, 50);
        byte[] before = bytes(memory);
        memory.position(3).limit(11);
        GlBufferMapping mapping = new GlBufferMapping(buffer, memory);
        buffer.setActiveMapping(mapping);
        Mockito.clearInvocations(gl);
        mapping.dispose();
        Mockito.verifyNoInteractions(gl);
        assertSame(mapping, buffer.getActiveMapping());
        assertSame(memory, mapping.getMemoryBuffer());
        assertEquals(3, memory.position());
        assertEquals(11, memory.limit());
        assertArrayEquals(before, bytes(memory));
    }

    // Reads and clears the same protected flag that dispose sets and write checks
    @Test
    void isDisposedReadsTheProtectedFlag() {
        ByteBuffer memory = guarded(4);
        Reopenable mapping = new Reopenable(new GlMutableBuffer(), memory);
        assertFalse(mapping.flag());
        assertFalse(mapping.isDisposed());
        mapping.dispose();
        assertTrue(mapping.flag());
        assertTrue(mapping.isDisposed());
        assertThrows(IllegalStateException.class, () -> mapping.write(data(4, 8), 0));
        mapping.reopen();
        assertFalse(mapping.isDisposed());
        ByteBuffer source = data(4, 8);
        mapping.write(source, 0);
        assertArrayEquals(copied(new byte[4], bytes(source), 0, 0, 4), bytes(memory));
    }

    // Returns the same memory buffer before and after writes and dispose
    @Test
    void getMemoryBufferReturnsTheSameInstance() {
        ByteBuffer memory = guarded(8);
        GlBufferMapping mapping = over(memory);
        assertSame(memory, mapping.getMemoryBuffer());
        mapping.write(data(8, 2), 0);
        assertSame(memory, mapping.getMemoryBuffer());
        assertEquals(0, memory.position());
        assertEquals(8, memory.limit());
        mapping.dispose();
        assertSame(memory, mapping.getMemoryBuffer());
    }

    // Keeps the public class with one public constructor and its public methods and protected flag
    @Test
    void classKeepsItsShape() throws ReflectiveOperationException {
        Class<GlBufferMapping> type = GlBufferMapping.class;
        assertEquals(Modifier.PUBLIC, type.getModifiers());
        assertSame(Object.class, type.getSuperclass());
        assertEquals(0, type.getInterfaces().length);

        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertEquals(Modifier.PUBLIC, constructors[0].getModifiers());
        assertArrayEquals(new Class<?>[] {GlBuffer.class, ByteBuffer.class}, constructors[0].getParameterTypes());

        Field flag = type.getDeclaredField("disposed");
        assertEquals(Modifier.PROTECTED, flag.getModifiers());
        assertSame(boolean.class, flag.getType());
        for (Field field : type.getDeclaredFields()) {
            if (!field.isSynthetic() && !field.equals(flag)) {
                assertEquals(Modifier.PRIVATE | Modifier.FINAL, field.getModifiers(), field.getName());
            }
        }

        List<String> methods = new ArrayList<>();
        for (Method method : type.getDeclaredMethods()) {
            if (!method.isSynthetic() && !Modifier.isPrivate(method.getModifiers())) {
                methods.add(method.getName());
            }
        }
        methods.sort(null);
        assertEquals(List.of("dispose", "getBufferObject", "getMemoryBuffer", "isDisposed", "write"), methods);
        assertPublic(type.getDeclaredMethod("write", ByteBuffer.class, int.class), void.class);
        assertPublic(type.getDeclaredMethod("getBufferObject"), GlBuffer.class);
        assertPublic(type.getDeclaredMethod("dispose"), void.class);
        assertPublic(type.getDeclaredMethod("isDisposed"), boolean.class);
        assertPublic(type.getDeclaredMethod("getMemoryBuffer"), ByteBuffer.class);
    }
}
