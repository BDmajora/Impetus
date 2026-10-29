package com.bdmajora.impetus.engine.impl.gl.functions;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapFlags;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapping;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferStorageFlags;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferUsage;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlImmutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.GLRenderDevice;
import com.bdmajora.impetus.engine.impl.gl.device.RenderDevice;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;
import com.bdmajora.impetus.lwjgl.GLExtension;
import com.bdmajora.impetus.lwjgl.LWJGLService;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

// Tests of the buffer map range strategies and of the strategy choice
class BufferMapRangeFunctionsTest {
    // GL enum of the array binding point
    private static final int ARRAY = GL15.GL_ARRAY_BUFFER;
    // GL enum of read only map access
    private static final int READ_ONLY = GL15.GL_READ_ONLY;
    // GL enum of write only map access
    private static final int WRITE_ONLY = GL15.GL_WRITE_ONLY;
    // GL enum of read and write map access
    private static final int READ_WRITE = GL15.GL_READ_WRITE;
    // Largest int as a long
    private static final long MAX = Integer.MAX_VALUE;
    // Message of the refusal of a deleted buffer
    private static final String DELETED = "Handle is not valid";
    // Message of the refusal of explicit flushes by the whole buffer strategy
    private static final String EXPLICIT = "Explicit flush not supported for MAP_FULL_AND_SLICE strategy";
    // Number of guard bytes on each side of a storage block
    private static final int GUARD = 64;
    // Value of every guard byte
    private static final byte GUARD_BYTE = (byte) 0xA5;
    // Map flags of a write mapping
    private static final EnumBitField<GlBufferMapFlags> WRITE = EnumBitField.of(GlBufferMapFlags.WRITE);
    // Map flags of a read and write mapping
    private static final EnumBitField<GlBufferMapFlags> READ_AND_WRITE = EnumBitField.of(GlBufferMapFlags.READ, GlBufferMapFlags.WRITE);
    // Map flags of a write mapping with explicit flushes
    private static final EnumBitField<GlBufferMapFlags> FLUSHED = EnumBitField.of(GlBufferMapFlags.WRITE, GlBufferMapFlags.EXPLICIT_FLUSH);
    // Range strategy
    private static final BufferMapRangeFunctions CORE = BufferMapRangeFunctions.CORE;
    // Whole buffer strategy
    private static final BufferMapRangeFunctions SLICE = BufferMapRangeFunctions.MAP_FULL_AND_SLICE;

    // Mocked LWJGL service that records every GL call
    private final LWJGLService gl = TestGl.gl();
    // Guarded native storage of each buffer name
    private final Map<Integer, Block> blocks = new HashMap<>();
    // Buffer names whose storage is mapped
    private final Set<Integer> mapped = new HashSet<>();
    // Buffer name bound to the array binding point
    private int bound;
    // Whole buffer mapping that the fake GL handed out last
    private ByteBuffer lastWhole;
    // Render device that a test activated or null when the test uses none
    private GLRenderDevice device;

    // Routes binds and maps and unmaps of the array binding point through the fake buffer storage
    @BeforeEach
    void open() {
        Mockito.doAnswer(call -> {
            int target = call.getArgument(0);
            if (target == ARRAY) {
                bound = call.getArgument(1);
            }
            return null;
        }).when(gl).glBindBuffer(anyInt(), anyInt());
        Mockito.doAnswer(call -> mapWhole(call.getArgument(0))).when(gl).glMapBuffer(anyInt(), anyInt());
        Mockito.doAnswer(call -> mapRange(call.getArgument(0), call.getArgument(1), call.getArgument(2)))
                .when(gl).glMapBufferRange(anyInt(), anyLong(), anyLong(), anyInt());
        Mockito.doAnswer(call -> {
            unmap(call.getArgument(0));
            return null;
        }).when(gl).glUnmapBuffer(anyInt());
    }

    // Deactivates the device and fails on an open mapping or a damaged guard and frees every block
    @AfterEach
    void release() {
        try {
            if (device != null) {
                device.makeInactive();
            }
            assertTrue(mapped.isEmpty(), "Mappings left open " + mapped);
            for (Map.Entry<Integer, Block> entry : blocks.entrySet()) {
                assertGuardsIntact(entry.getKey(), entry.getValue());
            }
        } finally {
            for (Block block : blocks.values()) {
                MemoryUtil.nmemFree(block.raw());
            }
        }
    }

    // Checks that null arguments are refused before any GL call
    @ParameterizedTest
    @EnumSource(BufferMapRangeFunctions.class)
    void nullArgumentsAreRefusedBeforeAnyCall(BufferMapRangeFunctions map) {
        GlMutableBuffer buffer = storage(16);
        refusedUntouched(NullPointerException.class, "Buffer must not be null", () -> map.mapBufferRange(null, 0L, 8L, WRITE));
        refusedUntouched(NullPointerException.class, "Flags must not be null", () -> map.mapBufferRange(buffer, 0L, 8L, null));
        // Reports the null buffer before the null flags and the null flags before a negative range
        refusedUntouched(NullPointerException.class, "Buffer must not be null", () -> map.mapBufferRange(null, -1L, -1L, null));
        refusedUntouched(NullPointerException.class, "Flags must not be null", () -> map.mapBufferRange(buffer, -1L, -1L, null));
    }

    // Returns every strategy paired with each negative or overflowing range or length past the int limit and its refusal message
    static Stream<Arguments> refusedRanges() {
        Object[][] ranges = {
                {-1L, 0L, "Offset must not be negative: -1"},
                {Long.MIN_VALUE, 8L, "Offset must not be negative: " + Long.MIN_VALUE},
                {-1L, -1L, "Offset must not be negative: -1"},
                {0L, -1L, "Length must not be negative: -1"},
                {8L, (long) Integer.MIN_VALUE, "Length must not be negative: " + Integer.MIN_VALUE},
                {0L, Long.MIN_VALUE, "Length must not be negative: " + Long.MIN_VALUE},
                {0L, MAX + 1L, "Length must not exceed 2147483647: 2147483648"},
                {0L, 1L << 32, "Length must not exceed 2147483647: 4294967296"},
                {0L, Long.MAX_VALUE, "Length must not exceed 2147483647: " + Long.MAX_VALUE},
                {Long.MAX_VALUE, Long.MAX_VALUE, "Length must not exceed 2147483647: " + Long.MAX_VALUE},
                {Long.MAX_VALUE, 1L, "Map range overflows: " + Long.MAX_VALUE + " + 1"},
                {Long.MAX_VALUE - 7L, 8L, "Map range overflows: " + (Long.MAX_VALUE - 7L) + " + 8"},
                {Long.MAX_VALUE - MAX + 1L, MAX, "Map range overflows: " + (Long.MAX_VALUE - MAX + 1L) + " + 2147483647"}};
        List<Arguments> rows = new ArrayList<>();
        for (BufferMapRangeFunctions map : BufferMapRangeFunctions.values()) {
            for (Object[] range : ranges) {
                rows.add(Arguments.of(map, range[0], range[1], range[2]));
            }
        }
        return rows.stream();
    }

    // Checks that a negative or overflowing range or a length past the int limit is refused before any GL call
    @ParameterizedTest
    @MethodSource("refusedRanges")
    void negativeOrOverflowingRangesAreRefusedBeforeAnyCall(BufferMapRangeFunctions map, long offset, long length, String message) {
        GlMutableBuffer buffer = storage(16);
        refusedUntouched(IllegalArgumentException.class, message, () -> map.mapBufferRange(buffer, offset, length, WRITE));
    }

    // Checks that a deleted mutable or immutable buffer is refused before any GL call
    @ParameterizedTest
    @EnumSource(BufferMapRangeFunctions.class)
    void deletedBuffersAreRefusedBeforeAnyCall(BufferMapRangeFunctions map) {
        GlMutableBuffer mutable = storage(16);
        GlImmutableBuffer immutable = immutable(16);
        mutable.delete();
        immutable.delete();
        refusedUntouched(IllegalStateException.class, DELETED, () -> map.mapBufferRange(mutable, 0L, 8L, WRITE));
        refusedUntouched(IllegalStateException.class, DELETED, () -> map.mapBufferRange(immutable, 0L, 16L, WRITE));
        refusedUntouched(IllegalStateException.class, DELETED, () -> map.mapBufferRange(mutable, 0L, 0L, WRITE));
        // Reports a negative range before the deleted buffer and the deleted buffer before explicit flushes
        refusedUntouched(IllegalArgumentException.class, "Length must not be negative: -4", () -> map.mapBufferRange(mutable, 0L, -4L, WRITE));
        refusedUntouched(IllegalStateException.class, DELETED, () -> map.mapBufferRange(mutable, 0L, 8L, FLUSHED));
    }

    // Returns ranges that the range strategy forwards unchanged including offsets past the int limit
    static Stream<Arguments> forwardedRanges() {
        return Stream.of(
                Arguments.of(0L, 0L),
                Arguments.of(0L, 1L),
                Arguments.of(7L, 9L),
                Arguments.of(63L, 1L),
                Arguments.of(64L, 0L),
                Arguments.of(0L, 64L),
                Arguments.of(MAX, 0L),
                Arguments.of(MAX, MAX),
                Arguments.of(MAX + 1L, 8L),
                Arguments.of(1L << 32, MAX),
                Arguments.of(Long.MAX_VALUE - MAX, MAX),
                Arguments.of(Long.MAX_VALUE, 0L));
    }

    // Checks that the range strategy forwards the range and the write bit to GL unchanged and returns what GL returns
    @ParameterizedTest
    @MethodSource("forwardedRanges")
    void coreForwardsTheRangeUnchanged(long offset, long length) {
        GlMutableBuffer buffer = storage(64);
        ByteBuffer answer = ByteBuffer.allocateDirect(1);
        Mockito.doReturn(answer).when(gl).glMapBufferRange(anyInt(), anyLong(), anyLong(), anyInt());
        Mockito.clearInvocations(gl);
        assertSame(answer, CORE.mapBufferRange(buffer, offset, length, WRITE));
        Mockito.verify(gl).glMapBufferRange(ARRAY, offset, length, GL30.GL_MAP_WRITE_BIT);
        Mockito.verifyNoMoreInteractions(gl);
    }

    // Checks that the range strategy forwards the bits of every flag set unchanged and returns null without an unmap when GL fails
    @Test
    void coreForwardsTheBitsOfEveryFlagSet() {
        GlImmutableBuffer buffer = immutable(64);
        Mockito.doReturn(null).when(gl).glMapBufferRange(anyInt(), anyLong(), anyLong(), anyInt());
        Mockito.clearInvocations(gl);
        List<EnumSet<GlBufferMapFlags>> sets = flagSets();
        for (EnumSet<GlBufferMapFlags> set : sets) {
            assertNull(CORE.mapBufferRange(buffer, 4L, 8L, field(set)));
            Mockito.verify(gl).glMapBufferRange(ARRAY, 4L, 8L, bits(set));
        }
        Mockito.verifyNoMoreInteractions(gl);
        assertEquals(255, sets.size());
    }

    // Checks that the whole buffer strategy maps with read and write access for both bits and write only access for the write bit and read only access otherwise
    @Test
    void sliceMapsTheWholeBufferWithTheAccessOfTheFlags() {
        GlMutableBuffer buffer = storage(16);
        int checked = 0;
        for (EnumSet<GlBufferMapFlags> set : flagSets()) {
            if (set.contains(GlBufferMapFlags.EXPLICIT_FLUSH)) {
                continue;
            }
            boolean read = set.contains(GlBufferMapFlags.READ);
            boolean write = set.contains(GlBufferMapFlags.WRITE);
            int access = read && write ? READ_WRITE : write ? WRITE_ONLY : READ_ONLY;
            Mockito.clearInvocations(gl);
            assertEquals(8, SLICE.mapBufferRange(buffer, 4L, 8L, field(set)).capacity());
            Mockito.verify(gl).glMapBuffer(ARRAY, access);
            Mockito.verifyNoMoreInteractions(gl);
            unmapArray();
            checked++;
        }
        assertEquals(127, checked);
    }

    // Checks that the whole buffer strategy refuses every flag set with explicit flushes before any GL call
    @Test
    void sliceRefusesExplicitFlushesBeforeAnyCall() {
        GlMutableBuffer buffer = storage(16);
        int checked = 0;
        for (EnumSet<GlBufferMapFlags> set : flagSets()) {
            if (set.contains(GlBufferMapFlags.EXPLICIT_FLUSH)) {
                refusedUntouched(UnsupportedOperationException.class, EXPLICIT, () -> SLICE.mapBufferRange(buffer, 0L, 8L, field(set)));
                checked++;
            }
        }
        assertEquals(128, checked);
        // Reports explicit flushes before a range end past the int limit and after a negative range
        refusedUntouched(UnsupportedOperationException.class, EXPLICIT, () -> SLICE.mapBufferRange(buffer, MAX, 8L, FLUSHED));
        refusedUntouched(IllegalArgumentException.class, "Offset must not be negative: -1", () -> SLICE.mapBufferRange(buffer, -1L, 8L, FLUSHED));
    }

    // Returns ranges that pass the shared checks and end past the int limit
    static Stream<Arguments> rangesPastTheIntLimit() {
        return Stream.of(
                Arguments.of(MAX + 1L, 0L),
                Arguments.of(MAX, 1L),
                Arguments.of(1L, MAX),
                Arguments.of(MAX - 3L, 4L),
                Arguments.of(1L << 32, 8L),
                Arguments.of(MAX, MAX),
                Arguments.of(Long.MAX_VALUE - MAX, MAX),
                Arguments.of(Long.MAX_VALUE, 0L));
    }

    // Checks that the whole buffer strategy refuses a range end past the int limit before any GL call
    @ParameterizedTest
    @MethodSource("rangesPastTheIntLimit")
    void sliceRefusesARangeEndPastTheIntLimitBeforeAnyCall(long offset, long length) {
        GlMutableBuffer buffer = storage(64);
        refusedUntouched(IllegalArgumentException.class, "Map range ends past 2147483647: " + (offset + length), () -> SLICE.mapBufferRange(buffer, offset, length, WRITE));
    }

    // Checks that the whole buffer strategy returns the mapping itself for a range from zero over the whole storage
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 63, 64, 4096})
    void sliceReturnsTheWholeMappingForTheWholeStorage(int size) {
        GlMutableBuffer buffer = storage(size);
        ByteBuffer view = SLICE.mapBufferRange(buffer, 0L, size, WRITE);
        assertSame(lastWhole, view);
        assertEquals(0, view.position());
        assertEquals(size, view.limit());
        assertEquals(size, view.capacity());
        assertEquals(ByteOrder.nativeOrder(), view.order());
        unmapArray();
    }

    // Returns ranges inside a storage of 64 bytes that the whole buffer strategy returns as views
    static Stream<Arguments> sliceRanges() {
        return Stream.of(
                Arguments.of(0, 0),
                Arguments.of(0, 1),
                Arguments.of(0, 63),
                Arguments.of(1, 63),
                Arguments.of(1, 62),
                Arguments.of(63, 1),
                Arguments.of(64, 0),
                Arguments.of(32, 32),
                Arguments.of(3, 5),
                Arguments.of(7, 9),
                Arguments.of(17, 31));
    }

    // Checks that a range view starts at the offset of the mapping and holds the length in native byte order and writes only its own bytes
    @ParameterizedTest
    @MethodSource("sliceRanges")
    void sliceReturnsANativeOrderViewOfTheRange(int offset, int length) {
        GlMutableBuffer buffer = storage(64);
        Block block = blocks.get(buffer.handle());
        byte[] before = bytes(block);
        ByteBuffer view = SLICE.mapBufferRange(buffer, offset, length, READ_AND_WRITE);
        assertNotSame(lastWhole, view);
        assertTrue(view.isDirect());
        assertFalse(view.isReadOnly());
        assertEquals(0, view.position());
        assertEquals(length, view.limit());
        assertEquals(length, view.capacity());
        assertEquals(block.first() + offset, MemoryUtil.memAddress(view));
        assertEquals(ByteOrder.nativeOrder(), view.order());
        if (length >= 4) {
            assertEquals(MemoryUtil.memGetInt(block.first() + offset), view.getInt(0));
        }
        for (int i = 0; i < length; i++) {
            view.put(i, (byte) ~view.get(i));
        }
        assertThrows(IndexOutOfBoundsException.class, () -> view.put(length, (byte) 0));
        assertThrows(IndexOutOfBoundsException.class, () -> view.get(-1));
        byte[] after = bytes(block);
        for (int i = 0; i < before.length; i++) {
            byte expected = i >= offset && i < offset + length ? (byte) ~before[i] : before[i];
            assertEquals(expected, after[i], "Byte " + i);
        }
        unmapArray();
    }

    // Returns storage sizes paired with ranges that pass the int limit and end past the storage
    static Stream<Arguments> rangesPastTheStorage() {
        return Stream.of(
                Arguments.of(64, 0L, 65L),
                Arguments.of(64, 1L, 64L),
                Arguments.of(64, 64L, 1L),
                Arguments.of(64, 65L, 0L),
                Arguments.of(64, MAX, 0L),
                Arguments.of(64, 0L, MAX),
                Arguments.of(64, MAX - 1L, 1L),
                Arguments.of(1, 1L, 1L),
                Arguments.of(0, 0L, 1L),
                Arguments.of(0, 1L, 0L));
    }

    // Checks that the whole buffer strategy unmaps the buffer and refuses a range end past the mapped storage
    @ParameterizedTest
    @MethodSource("rangesPastTheStorage")
    void sliceUnmapsAndRefusesARangePastTheStorage(int size, long offset, long length) {
        GlMutableBuffer buffer = storage(size);
        Mockito.clearInvocations(gl);
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> SLICE.mapBufferRange(buffer, offset, length, WRITE));
        assertEquals("Map range ends past the buffer: " + (offset + length) + " > " + size, thrown.getMessage());
        assertEquals(0, thrown.getSuppressed().length);
        InOrder order = Mockito.inOrder(gl);
        order.verify(gl).glMapBuffer(ARRAY, WRITE_ONLY);
        order.verify(gl).glUnmapBuffer(ARRAY);
        Mockito.verifyNoMoreInteractions(gl);
        assertTrue(mapped.isEmpty());
    }

    // Checks that a failed unmap of either kind is suppressed into the refusal of a range past the storage
    @Test
    void sliceKeepsTheRefusalWhenTheUnmapFails() {
        GlMutableBuffer buffer = storage(16);
        Throwable[] failures = {new IllegalStateException("Unmap failed"), new StackOverflowError("Unmap overflowed")};
        for (Throwable failure : failures) {
            Mockito.doThrow(failure).when(gl).glUnmapBuffer(anyInt());
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> SLICE.mapBufferRange(buffer, 8L, 9L, WRITE));
            assertEquals("Map range ends past the buffer: 17 > 16", thrown.getMessage());
            assertArrayEquals(new Throwable[] {failure}, thrown.getSuppressed());
            // Closes the mapping that the failed unmap left open in the fake
            mapped.clear();
        }
        Mockito.verify(gl, Mockito.times(2)).glUnmapBuffer(ARRAY);
    }

    // Checks that the whole buffer strategy compares the range with the mapped storage and not with the recorded size of a mutable buffer
    @Test
    void sliceIgnoresTheRecordedSize() {
        GlMutableBuffer larger = storage(32);
        larger.setSize(64L);
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> SLICE.mapBufferRange(larger, 0L, 64L, WRITE));
        assertEquals("Map range ends past the buffer: 64 > 32", thrown.getMessage());
        assertTrue(mapped.isEmpty());

        GlMutableBuffer smaller = storage(64);
        smaller.setSize(32L);
        ByteBuffer view = SLICE.mapBufferRange(smaller, 0L, 32L, WRITE);
        assertNotSame(lastWhole, view);
        assertEquals(32, view.capacity());
        unmapArray();

        GlMutableBuffer unsized = new GlMutableBuffer();
        store(unsized, 16);
        bind(unsized);
        ByteBuffer unsizedView = SLICE.mapBufferRange(unsized, 0L, 16L, WRITE);
        assertSame(lastWhole, unsizedView);
        assertEquals(16, unsizedView.capacity());
        unmapArray();

        GlImmutableBuffer immutable = immutable(48);
        ByteBuffer immutableView = SLICE.mapBufferRange(immutable, 0L, 48L, WRITE);
        assertSame(lastWhole, immutableView);
        assertEquals(48, immutableView.capacity());
        unmapArray();
    }

    // Checks that the whole buffer strategy returns null without an unmap when GL fails to map the buffer
    @Test
    void sliceReturnsNullWhenGlFailsToMap() {
        GlMutableBuffer buffer = storage(16);
        assertNotNull(SLICE.mapBufferRange(buffer, 0L, 8L, WRITE));
        Mockito.clearInvocations(gl);
        // Maps the buffer a second time while the first mapping is open
        assertNull(SLICE.mapBufferRange(buffer, 0L, 8L, WRITE));
        // Maps a range past the storage that a successful map would refuse
        assertNull(SLICE.mapBufferRange(buffer, 8L, 9L, WRITE));
        Mockito.verify(gl, Mockito.times(2)).glMapBuffer(ARRAY, WRITE_ONLY);
        Mockito.verifyNoMoreInteractions(gl);
        unmapArray();
        // Maps with no buffer bound to the array binding point
        bound = 0;
        Mockito.clearInvocations(gl);
        assertNull(SLICE.mapBufferRange(buffer, 0L, 8L, WRITE));
        Mockito.verify(gl, Mockito.never()).glUnmapBuffer(anyInt());
    }

    // Checks that both strategies map the same buffer again after each unmap and that every write stays in the storage
    @ParameterizedTest
    @EnumSource(BufferMapRangeFunctions.class)
    void repeatedMapsAfterEachUnmapKeepEarlierWrites(BufferMapRangeFunctions map) {
        GlMutableBuffer buffer = storage(64);
        Block block = blocks.get(buffer.handle());
        Mockito.clearInvocations(gl);
        for (int i = 0; i < 64; i++) {
            ByteBuffer view = map.mapBufferRange(buffer, i, 64 - i, WRITE);
            assertEquals(block.first() + i, MemoryUtil.memAddress(view));
            assertEquals(64 - i, view.capacity());
            view.put(0, (byte) (i + 100));
            unmapArray();
        }
        for (int i = 0; i < 64; i++) {
            assertEquals((byte) (i + 100), MemoryUtil.memGetByte(block.first() + i));
        }
        Mockito.verify(gl, Mockito.times(64)).glUnmapBuffer(ARRAY);
    }

    // Checks that a legacy device unmaps after a range past the storage or the int limit and maps the buffer again
    @Test
    void legacyDeviceMapsAgainAfterARefusedRange() {
        CommandList list = legacyList();
        GlMutableBuffer buffer = list.createMutableBuffer();
        list.allocateStorage(buffer, 64L, GlBufferUsage.STATIC_DRAW);
        store(buffer, 64);
        Block block = blocks.get(buffer.handle());

        IllegalArgumentException past = assertThrows(IllegalArgumentException.class, () -> list.mapBuffer(buffer, 8L, 64L, WRITE));
        assertEquals("Map range ends past the buffer: 72 > 64", past.getMessage());
        IllegalArgumentException beyond = assertThrows(IllegalArgumentException.class, () -> list.mapBuffer(buffer, 1L << 31, 8L, WRITE));
        assertEquals("Map range ends past 2147483647: 2147483656", beyond.getMessage());
        assertNull(buffer.getActiveMapping());
        assertTrue(mapped.isEmpty());

        GlBufferMapping range = list.mapBuffer(buffer, 8L, 56L, WRITE);
        assertSame(range, buffer.getActiveMapping());
        assertEquals(block.first() + 8L, MemoryUtil.memAddress(range.getMemoryBuffer()));
        assertEquals(56, range.getMemoryBuffer().capacity());
        list.unmap(range);
        assertTrue(range.isDisposed());

        GlBufferMapping whole = list.mapBuffer(buffer, 0L, 64L, WRITE);
        assertEquals(block.first(), MemoryUtil.memAddress(whole.getMemoryBuffer()));
        assertEquals(64, whole.getMemoryBuffer().capacity());
        list.unmap(whole);
        assertTrue(mapped.isEmpty());
    }

    // Checks that the range strategy is picked when GL 3.0 or ARB_map_buffer_range is present and the whole buffer strategy otherwise
    @ParameterizedTest
    @CsvSource({"true, true, CORE", "true, false, CORE", "false, true, CORE", "false, false, MAP_FULL_AND_SLICE"})
    void pickBestFollowsTheMapCapabilities(boolean version, boolean extension, BufferMapRangeFunctions expected) {
        // Forgets the choice that the shared device makes when the interface first loads
        RenderDevice renderDevice = Mockito.mock(RenderDevice.class);
        Mockito.clearInvocations(gl);
        Mockito.doReturn(version).when(gl).isOpenGLVersionSupported(3, 0);
        Mockito.doReturn(extension).when(gl).isExtensionSupported(GLExtension.ARB_map_buffer_range);
        assertSame(expected, BufferMapRangeFunctions.pickBest(renderDevice));
        Mockito.verify(gl).isOpenGLVersionSupported(3, 0);
        Mockito.verify(gl, Mockito.times(version ? 0 : 1)).isExtensionSupported(GLExtension.ARB_map_buffer_range);
        Mockito.verifyNoMoreInteractions(gl);
        Mockito.verifyNoInteractions(renderDevice);
    }

    // Checks that the choice ignores a null device and reads the capabilities again on every call
    @Test
    void pickBestIgnoresTheDeviceAndReadsTheCapabilitiesEveryCall() {
        assertSame(CORE, BufferMapRangeFunctions.pickBest(null));
        Mockito.doReturn(false).when(gl).isOpenGLVersionSupported(anyInt(), anyInt());
        Mockito.doReturn(false).when(gl).isExtensionSupported(any());
        assertSame(SLICE, BufferMapRangeFunctions.pickBest(null));
        assertSame(SLICE, BufferMapRangeFunctions.pickBest(null));
        Mockito.doReturn(true).when(gl).isExtensionSupported(GLExtension.ARB_map_buffer_range);
        assertSame(CORE, BufferMapRangeFunctions.pickBest(null));
        Mockito.verify(gl, Mockito.times(4)).isOpenGLVersionSupported(3, 0);
    }

    // Checks the declared strategies and their lookup by name
    @Test
    void strategiesKeepTheirOrderAndNames() {
        assertArrayEquals(new BufferMapRangeFunctions[] {CORE, SLICE}, BufferMapRangeFunctions.values());
        assertNotSame(BufferMapRangeFunctions.values(), BufferMapRangeFunctions.values());
        assertSame(CORE, BufferMapRangeFunctions.valueOf("CORE"));
        assertSame(SLICE, BufferMapRangeFunctions.valueOf("MAP_FULL_AND_SLICE"));
        assertThrows(IllegalArgumentException.class, () -> BufferMapRangeFunctions.valueOf("core"));
        assertThrows(IllegalArgumentException.class, () -> BufferMapRangeFunctions.valueOf(""));
        assertThrows(NullPointerException.class, () -> BufferMapRangeFunctions.valueOf(null));
        for (BufferMapRangeFunctions map : BufferMapRangeFunctions.values()) {
            assertSame(BufferMapRangeFunctions.class, map.getDeclaringClass());
        }
    }

    // Checks that the public map method and the public choice keep their signatures
    @Test
    void publicMembersKeepTheirSignatures() throws ReflectiveOperationException {
        Method map = BufferMapRangeFunctions.class.getMethod("mapBufferRange", GlBuffer.class, long.class, long.class, EnumBitField.class);
        assertEquals(Modifier.PUBLIC | Modifier.ABSTRACT, map.getModifiers());
        assertSame(ByteBuffer.class, map.getReturnType());
        Method pick = BufferMapRangeFunctions.class.getMethod("pickBest", RenderDevice.class);
        assertEquals(Modifier.PUBLIC | Modifier.STATIC, pick.getModifiers());
        assertSame(BufferMapRangeFunctions.class, pick.getReturnType());
    }

    // Asserts that the call throws the type with the message and makes no GL call
    private <T extends Throwable> void refusedUntouched(Class<T> type, String message, Executable call) {
        Mockito.clearInvocations(gl);
        T thrown = assertThrows(type, call);
        assertEquals(message, thrown.getMessage());
        Mockito.verifyNoInteractions(gl);
    }

    // Returns an active render device command list whose device picked the whole buffer strategy
    private CommandList legacyList() {
        Mockito.doReturn(false).when(gl).isOpenGLVersionSupported(anyInt(), anyInt());
        Mockito.doReturn(false).when(gl).isExtensionSupported(any());
        device = new GLRenderDevice();
        device.makeActive();
        assertSame(SLICE, device.getDeviceFunctions().bufferMapRangeFunctions());
        return device.createCommandList();
    }

    // Creates a mutable buffer with storage and a recorded size of the given number of bytes and binds it to the array binding point
    private GlMutableBuffer storage(int size) {
        GlMutableBuffer buffer = new GlMutableBuffer();
        buffer.setSize(size);
        store(buffer, size);
        bind(buffer);
        return buffer;
    }

    // Creates an immutable buffer with storage of the given number of bytes and binds it to the array binding point
    private GlImmutableBuffer immutable(int size) {
        GlImmutableBuffer buffer = new GlImmutableBuffer(EnumBitField.of(GlBufferStorageFlags.MAP_WRITE));
        store(buffer, size);
        bind(buffer);
        return buffer;
    }

    // Backs the buffer with guarded native storage of the given number of bytes that follow a fixed pattern
    private void store(GlBuffer buffer, int size) {
        long raw = MemoryUtil.nmemAlloc(size + 2L * GUARD);
        MemoryUtil.memSet(raw, GUARD_BYTE, size + 2L * GUARD);
        for (int i = 0; i < size; i++) {
            MemoryUtil.memPutByte(raw + GUARD + i, (byte) (i * 7 + 1));
        }
        blocks.put(buffer.handle(), new Block(raw, size));
    }

    // Binds the buffer to the array binding point of the fake GL without a GL call
    private void bind(GlBuffer buffer) {
        bound = buffer.handle();
    }

    // Unmaps the buffer bound to the array binding point through the GL mock
    private void unmapArray() {
        gl.glUnmapBuffer(ARRAY);
    }

    // Returns a native order view of the whole storage of the bound buffer or null for no storage or a buffer that is already mapped
    private ByteBuffer mapWhole(int target) {
        assertEquals(ARRAY, target);
        Block block = blocks.get(bound);
        if (block == null || !mapped.add(bound)) {
            return null;
        }
        lastWhole = MemoryUtil.memByteBuffer(block.first(), block.size()).order(ByteOrder.nativeOrder());
        return lastWhole;
    }

    // Returns a native order view of the range of the bound buffer or null for a range that GL rejects or a buffer that is already mapped
    private ByteBuffer mapRange(int target, long offset, long length) {
        assertEquals(ARRAY, target);
        Block block = blocks.get(bound);
        if (block == null || length <= 0L || offset > block.size() - length || !mapped.add(bound)) {
            return null;
        }
        return MemoryUtil.memByteBuffer(block.first() + offset, (int) length).order(ByteOrder.nativeOrder());
    }

    // Unmaps the buffer bound to the array binding point and fails when it is not mapped
    private void unmap(int target) {
        assertEquals(ARRAY, target);
        assertTrue(mapped.remove(bound), "Unmap of buffer " + bound + " that is not mapped");
    }

    // Returns every non empty set of map flags
    private static List<EnumSet<GlBufferMapFlags>> flagSets() {
        GlBufferMapFlags[] all = GlBufferMapFlags.values();
        List<EnumSet<GlBufferMapFlags>> sets = new ArrayList<>();
        for (int mask = 1; mask < 1 << all.length; mask++) {
            EnumSet<GlBufferMapFlags> set = EnumSet.noneOf(GlBufferMapFlags.class);
            for (int i = 0; i < all.length; i++) {
                if ((mask & 1 << i) != 0) {
                    set.add(all[i]);
                }
            }
            sets.add(set);
        }
        return sets;
    }

    // Returns the bit field of the flag set
    private static EnumBitField<GlBufferMapFlags> field(EnumSet<GlBufferMapFlags> set) {
        return EnumBitField.of(set.toArray(new GlBufferMapFlags[0]));
    }

    // Returns the GL bits of the flag set
    private static int bits(EnumSet<GlBufferMapFlags> set) {
        int bits = 0;
        for (GlBufferMapFlags flag : set) {
            bits |= flag.getBits();
        }
        return bits;
    }

    // Returns a copy of the stored bytes of the block
    private static byte[] bytes(Block block) {
        byte[] bytes = new byte[block.size()];
        MemoryUtil.memByteBuffer(block.first(), block.size()).get(bytes);
        return bytes;
    }

    // Fails when a guard byte of the block was written
    private static void assertGuardsIntact(int name, Block block) {
        for (int i = 0; i < GUARD; i++) {
            assertEquals(GUARD_BYTE, MemoryUtil.memGetByte(block.raw() + i), "Guard byte " + i + " before buffer " + name);
            assertEquals(GUARD_BYTE, MemoryUtil.memGetByte(block.first() + block.size() + i), "Guard byte " + i + " after buffer " + name);
        }
    }

    // Native storage of one buffer of the given number of bytes between two guard zones
    private record Block(long raw, int size) {
        // Returns the address of the first stored byte
        long first() {
            return raw + GUARD;
        }
    }
}
