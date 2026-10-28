package com.bdmajora.impetus.engine.impl.gl.functions;

import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapping;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferStorageFlags;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
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
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL31;
import org.lwjgl.system.MemoryUtil;
import org.mockito.InOrder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
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

// Tests of the buffer copy strategies and of the strategy choice
class BufferCopyFunctionsTest {
    // GL enum of the copy read binding point
    private static final int COPY_READ = GL31.GL_COPY_READ_BUFFER;
    // GL enum of the copy write binding point
    private static final int COPY_WRITE = GL31.GL_COPY_WRITE_BUFFER;
    // GL enum of the pixel pack binding point
    private static final int PACK = GL21.GL_PIXEL_PACK_BUFFER;
    // GL enum of the pixel unpack binding point
    private static final int UNPACK = GL21.GL_PIXEL_UNPACK_BUFFER;
    // GL enum of read only map access
    private static final int READ_ONLY = GL15.GL_READ_ONLY;
    // GL enum of write only map access
    private static final int WRITE_ONLY = GL15.GL_WRITE_ONLY;
    // GL enum of read and write map access
    private static final int READ_WRITE = GL15.GL_READ_WRITE;
    // Message of the refusal of a mapped buffer
    private static final String MAPPED = "Cannot use PIXEL_PACK copy strategy on mapped buffers";
    // Message of the refusal of a deleted buffer
    private static final String DELETED = "Handle is not valid";
    // Number of guard bytes on each side of a storage block
    private static final int GUARD = 64;
    // Value of every guard byte
    private static final byte GUARD_BYTE = (byte) 0xA5;

    // Mocked LWJGL service that records every GL call
    private final LWJGLService gl = TestGl.gl();
    // Command list mock that forwards every bind to the GL mock
    private final CommandList list = Mockito.mock(CommandList.class);
    // Storage block of each buffer name
    private final Map<Integer, Block> blocks = new HashMap<>();
    // Buffer name bound to each GL binding point
    private final Map<Integer, Integer> bindings = new HashMap<>();
    // Buffer names whose storage is mapped
    private final Set<Integer> mapped = new HashSet<>();
    // Static MemoryUtil mock that checks every native copy against the open mappings
    private MockedStatic<MemoryUtil> memory;
    // Render device that a test activated or null when the test uses none
    private GLRenderDevice device;
    // Number of native copies that reached the static mock
    private int copies;

    // Opens the checked memory mock and routes binds and maps and unmaps through the fake buffer storage
    @BeforeEach
    void open() {
        memory = Mockito.mockStatic(MemoryUtil.class, this::memoryCall);
        Mockito.doAnswer(this::forwardBind).when(list).bindBuffer(any(), any());
        Mockito.doAnswer(call -> {
            bindings.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(gl).glBindBuffer(anyInt(), anyInt());
        Mockito.doAnswer(call -> map(call.getArgument(0))).when(gl).nglMapBuffer(anyInt(), anyInt());
        Mockito.doAnswer(call -> {
            unmap(call.getArgument(0));
            return null;
        }).when(gl).glUnmapBuffer(anyInt());
    }

    // Deactivates the device and fails on an open mapping or a damaged guard and frees every block and closes the memory mock
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
            memory.close();
        }
    }

    // Checks that null arguments are refused before any bind or GL call
    @ParameterizedTest
    @EnumSource(BufferCopyFunctions.class)
    void nullArgumentsAreRefusedBeforeAnyCall(BufferCopyFunctions copy) {
        GlMutableBuffer buffer = buffer(16, 1);
        refusedUntouched(NullPointerException.class, "Command list must not be null", () -> copy.copyBufferSubData(null, buffer, buffer, 0L, 8L, 4L));
        refusedUntouched(NullPointerException.class, "Source buffer must not be null", () -> copy.copyBufferSubData(list, null, buffer, 0L, 8L, 4L));
        refusedUntouched(NullPointerException.class, "Destination buffer must not be null", () -> copy.copyBufferSubData(list, buffer, null, 0L, 8L, 4L));
        // Reports the first null argument before a negative range
        refusedUntouched(NullPointerException.class, "Command list must not be null", () -> copy.copyBufferSubData(null, null, null, -1L, -1L, -1L));
        refusedUntouched(NullPointerException.class, "Source buffer must not be null", () -> copy.copyBufferSubData(list, null, null, -1L, -1L, -1L));
    }

    // Returns every strategy paired with each negative or overflowing range and its refusal message
    static Stream<Arguments> refusedRanges() {
        List<Arguments> rows = new ArrayList<>();
        Object[][] ranges = {
                {-1L, 0L, 0L, "Read offset must not be negative: -1"},
                {Long.MIN_VALUE, 0L, 0L, "Read offset must not be negative: " + Long.MIN_VALUE},
                {-1L, -1L, -1L, "Read offset must not be negative: -1"},
                {0L, -1L, 0L, "Write offset must not be negative: -1"},
                {0L, Long.MIN_VALUE, 8L, "Write offset must not be negative: " + Long.MIN_VALUE},
                {0L, 0L, -1L, "Size must not be negative: -1"},
                {0L, 0L, (long) Integer.MIN_VALUE, "Size must not be negative: " + Integer.MIN_VALUE},
                {0L, 0L, Long.MIN_VALUE, "Size must not be negative: " + Long.MIN_VALUE},
                {Long.MAX_VALUE - 7L, 0L, 8L, "Copy range overflows: " + (Long.MAX_VALUE - 7L) + " + 8"},
                {Long.MAX_VALUE, 0L, 1L, "Copy range overflows: " + Long.MAX_VALUE + " + 1"},
                {0L, Long.MAX_VALUE, 1L, "Copy range overflows: " + Long.MAX_VALUE + " + 1"},
                {1L, Long.MAX_VALUE, Long.MAX_VALUE, "Copy range overflows: " + Long.MAX_VALUE + " + " + Long.MAX_VALUE}};
        for (BufferCopyFunctions copy : BufferCopyFunctions.values()) {
            for (Object[] range : ranges) {
                rows.add(Arguments.of(copy, range[0], range[1], range[2], range[3]));
            }
        }
        return rows.stream();
    }

    // Checks that a negative or overflowing range is refused before any bind or GL call or native copy
    @ParameterizedTest
    @MethodSource("refusedRanges")
    void negativeOrOverflowingRangesAreRefusedBeforeAnyCall(BufferCopyFunctions copy, long readOffset, long writeOffset, long bytes, String message) {
        GlMutableBuffer src = buffer(16, 1);
        GlMutableBuffer dst = buffer(16, 2);
        refusedUntouched(IllegalArgumentException.class, message, () -> copy.copyBufferSubData(list, src, dst, readOffset, writeOffset, bytes));
    }

    // Checks that a deleted source or destination is refused before any bind or GL call
    @ParameterizedTest
    @EnumSource(BufferCopyFunctions.class)
    void deletedBuffersAreRefusedBeforeAnyCall(BufferCopyFunctions copy) {
        GlMutableBuffer live = buffer(16, 1);
        GlMutableBuffer deleted = buffer(16, 2);
        deleted.delete();
        refusedUntouched(IllegalStateException.class, DELETED, () -> copy.copyBufferSubData(list, deleted, live, 0L, 0L, 4L));
        refusedUntouched(IllegalStateException.class, DELETED, () -> copy.copyBufferSubData(list, live, deleted, 0L, 0L, 4L));
        refusedUntouched(IllegalStateException.class, DELETED, () -> copy.copyBufferSubData(list, deleted, deleted, 0L, 8L, 4L));
        refusedUntouched(IllegalStateException.class, DELETED, () -> copy.copyBufferSubData(list, deleted, live, 0L, 0L, 0L));
        // Reports a negative range before the deleted buffer
        refusedUntouched(IllegalArgumentException.class, "Size must not be negative: -4", () -> copy.copyBufferSubData(list, deleted, live, 0L, 0L, -4L));
    }

    // Returns copy ranges that the GPU strategy forwards unchanged
    static Stream<Arguments> forwardedRanges() {
        return Stream.of(
                Arguments.of(0L, 0L, 0L),
                Arguments.of(0L, 0L, 1L),
                Arguments.of(1L, 3L, 5L),
                Arguments.of(15L, 15L, 1L),
                Arguments.of(16L, 0L, 0L),
                Arguments.of(Long.MAX_VALUE - 8L, 0L, 8L),
                Arguments.of(0L, Long.MAX_VALUE - 8L, 8L),
                Arguments.of(Long.MAX_VALUE, Long.MAX_VALUE, 0L),
                Arguments.of((long) Integer.MAX_VALUE, (long) Integer.MAX_VALUE, (long) Integer.MAX_VALUE),
                Arguments.of(1L << 31, 1L << 32, (1L << 31) + 1L));
    }

    // Checks that the GPU strategy binds both copy binding points and forwards the range unchanged
    @ParameterizedTest
    @MethodSource("forwardedRanges")
    void coreBindsTheCopyPointsAndForwardsTheRange(long readOffset, long writeOffset, long bytes) {
        GlMutableBuffer src = buffer(16, 1);
        GlMutableBuffer dst = buffer(16, 2);
        Mockito.clearInvocations(gl, list);
        BufferCopyFunctions.CORE.copyBufferSubData(list, src, dst, readOffset, writeOffset, bytes);
        InOrder order = Mockito.inOrder(list, gl);
        order.verify(list).bindBuffer(GlBufferTarget.COPY_READ_BUFFER, src);
        order.verify(list).bindBuffer(GlBufferTarget.COPY_WRITE_BUFFER, dst);
        order.verify(gl).glCopyBufferSubData(COPY_READ, COPY_WRITE, readOffset, writeOffset, bytes);
        Mockito.verifyNoMoreInteractions(list);
        Mockito.verify(gl, Mockito.never()).nglMapBuffer(anyInt(), anyInt());
        assertEquals(0, copies);
    }

    // Checks that the GPU strategy leaves overlap and mapping and size checks to GL
    @Test
    void coreLeavesOverlapMappingAndSizeChecksToGl() {
        GlMutableBuffer buffer = buffer(16, 1);
        BufferCopyFunctions.CORE.copyBufferSubData(list, buffer, buffer, 0L, 4L, 8L);
        Mockito.verify(gl).glCopyBufferSubData(COPY_READ, COPY_WRITE, 0L, 4L, 8L);
        buffer.setActiveMapping(new GlBufferMapping(buffer, ByteBuffer.allocateDirect(16)));
        GlImmutableBuffer immutable = new GlImmutableBuffer(EnumBitField.of(GlBufferStorageFlags.MAP_WRITE));
        BufferCopyFunctions.CORE.copyBufferSubData(list, buffer, immutable, 8L, 64L, 32L);
        Mockito.verify(gl).glCopyBufferSubData(COPY_READ, COPY_WRITE, 8L, 64L, 32L);
        Mockito.verify(list, Mockito.times(2)).bindBuffer(GlBufferTarget.COPY_READ_BUFFER, buffer);
        Mockito.verify(list).bindBuffer(GlBufferTarget.COPY_WRITE_BUFFER, immutable);
        assertEquals(0, copies);
    }

    // Checks that a failed bind of the GPU strategy issues no copy and that later calls copy each time
    @Test
    void coreBindFailureIssuesNoCopyAndLaterCallsCopyEachTime() {
        GlMutableBuffer src = buffer(16, 1);
        GlMutableBuffer dst = buffer(16, 2);
        RuntimeException failure = new RuntimeException("Bind failed");
        Mockito.doThrow(failure).when(list).bindBuffer(GlBufferTarget.COPY_WRITE_BUFFER, dst);
        assertSame(failure, assertThrows(RuntimeException.class, () -> BufferCopyFunctions.CORE.copyBufferSubData(list, src, dst, 0L, 0L, 4L)));
        Mockito.verify(gl, Mockito.never()).glCopyBufferSubData(anyInt(), anyInt(), anyLong(), anyLong(), anyLong());
        Mockito.doAnswer(this::forwardBind).when(list).bindBuffer(GlBufferTarget.COPY_WRITE_BUFFER, dst);
        BufferCopyFunctions.CORE.copyBufferSubData(list, src, dst, 0L, 0L, 4L);
        BufferCopyFunctions.CORE.copyBufferSubData(list, src, dst, 0L, 0L, 4L);
        Mockito.verify(gl, Mockito.times(2)).glCopyBufferSubData(COPY_READ, COPY_WRITE, 0L, 0L, 4L);
    }

    // Returns source and destination sizes with a copy range inside both
    static Stream<Arguments> pixelRanges() {
        return Stream.of(
                Arguments.of(64, 48, 0L, 0L, 1L),
                Arguments.of(64, 48, 63L, 47L, 1L),
                Arguments.of(64, 48, 0L, 0L, 48L),
                Arguments.of(64, 48, 16L, 0L, 48L),
                Arguments.of(64, 48, 1L, 3L, 7L),
                Arguments.of(64, 48, 5L, 40L, 8L),
                Arguments.of(1, 1, 0L, 0L, 1L),
                Arguments.of(8192, 8192, 1L, 2L, 8189L),
                Arguments.of(8192, 4099, 4093L, 3L, 4096L));
    }

    // Checks that the mapping strategy copies the range between the two mappings and unmaps and unbinds both buffers
    @ParameterizedTest
    @MethodSource("pixelRanges")
    void pixelPackCopiesTheRangeBetweenTwoMappings(int srcSize, int dstSize, long readOffset, long writeOffset, long bytes) {
        GlMutableBuffer src = buffer(srcSize, 1);
        GlMutableBuffer dst = buffer(dstSize, 2);
        byte[] source = bytes(src);
        byte[] expected = bytes(dst);
        System.arraycopy(source, (int) readOffset, expected, (int) writeOffset, (int) bytes);
        Mockito.clearInvocations(gl, list);
        BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, readOffset, writeOffset, bytes);
        assertArrayEquals(source, bytes(src));
        assertArrayEquals(expected, bytes(dst));
        InOrder order = Mockito.inOrder(list, gl);
        order.verify(list).bindBuffer(GlBufferTarget.PIXEL_PACK_BUFFER, src);
        order.verify(list).bindBuffer(GlBufferTarget.PIXEL_UNPACK_BUFFER, dst);
        order.verify(gl).nglMapBuffer(PACK, READ_ONLY);
        order.verify(gl).nglMapBuffer(UNPACK, WRITE_ONLY);
        order.verify(list).bindBuffer(GlBufferTarget.PIXEL_PACK_BUFFER, null);
        order.verify(list).bindBuffer(GlBufferTarget.PIXEL_UNPACK_BUFFER, null);
        Mockito.verifyNoMoreInteractions(list);
        Mockito.verify(gl).glUnmapBuffer(PACK);
        Mockito.verify(gl).glUnmapBuffer(UNPACK);
        Mockito.verify(gl, Mockito.never()).glCopyBufferSubData(anyInt(), anyInt(), anyLong(), anyLong(), anyLong());
        assertEquals(1, copies);
        assertPixelPointsUnbound();
    }

    // Returns ranges that do not overlap within one 64 byte buffer
    static Stream<Arguments> disjointRanges() {
        return Stream.of(
                Arguments.of(0L, 32L, 32L),
                Arguments.of(32L, 0L, 32L),
                Arguments.of(0L, 63L, 1L),
                Arguments.of(63L, 0L, 1L),
                Arguments.of(1L, 17L, 13L),
                Arguments.of(10L, 0L, 10L),
                Arguments.of(0L, 10L, 10L));
    }

    // Checks that the mapping strategy copies within one buffer through one read and write mapping
    @ParameterizedTest
    @MethodSource("disjointRanges")
    void pixelPackCopiesWithinOneBufferThroughOneMapping(long readOffset, long writeOffset, long bytes) {
        GlMutableBuffer buffer = buffer(64, 3);
        byte[] expected = bytes(buffer);
        System.arraycopy(expected, (int) readOffset, expected, (int) writeOffset, (int) bytes);
        Mockito.clearInvocations(gl, list);
        BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, buffer, buffer, readOffset, writeOffset, bytes);
        assertArrayEquals(expected, bytes(buffer));
        Mockito.verify(gl).nglMapBuffer(PACK, READ_WRITE);
        Mockito.verify(gl).nglMapBuffer(anyInt(), anyInt());
        Mockito.verify(gl).glUnmapBuffer(PACK);
        Mockito.verify(gl).glUnmapBuffer(anyInt());
        Mockito.verify(list, Mockito.never()).bindBuffer(GlBufferTarget.PIXEL_UNPACK_BUFFER, buffer);
        assertEquals(1, copies);
        assertPixelPointsUnbound();
    }

    // Checks that the mapping strategy refuses overlapping ranges within one buffer before any bind or GL call
    @ParameterizedTest
    @CsvSource({"0, 0, 1", "0, 1, 2", "1, 0, 2", "8, 8, 16", "0, 31, 32", "31, 0, 32", "0, 0, 64", "63, 63, 1"})
    void pixelPackRefusesOverlappingRangesWithinOneBuffer(long readOffset, long writeOffset, long bytes) {
        GlMutableBuffer buffer = buffer(64, 3);
        String message = "Copy ranges overlap within one buffer: read " + readOffset + " write " + writeOffset + " size " + bytes;
        refusedUntouched(IllegalArgumentException.class, message, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, buffer, buffer, readOffset, writeOffset, bytes));
    }

    // Checks that a range within one buffer that overlaps and ends past the buffer reports the end first
    @Test
    void pixelPackReportsTheEndBeforeTheOverlapWithinOneBuffer() {
        GlMutableBuffer buffer = buffer(64, 3);
        refusedUntouched(IllegalArgumentException.class, "Copy range ends past the source buffer: 68 > 64",
                () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, buffer, buffer, 60L, 56L, 8L));
        refusedUntouched(IllegalArgumentException.class, "Copy range ends past the destination buffer: 68 > 64",
                () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, buffer, buffer, 56L, 60L, 8L));
    }

    // Returns ranges past the end of a 16 byte source or an 8 byte destination with the refusal message
    static Stream<Arguments> pastTheEnd() {
        String source = "Copy range ends past the source buffer: ";
        String destination = "Copy range ends past the destination buffer: ";
        return Stream.of(
                Arguments.of(0L, 0L, 17L, source + "17 > 16"),
                Arguments.of(16L, 0L, 1L, source + "17 > 16"),
                Arguments.of(17L, 0L, 0L, source + "17 > 16"),
                Arguments.of((long) Integer.MAX_VALUE, 0L, 1L, source + "2147483648 > 16"),
                Arguments.of(Long.MAX_VALUE - 1L, 0L, 1L, source + Long.MAX_VALUE + " > 16"),
                Arguments.of(Long.MAX_VALUE, 0L, 0L, source + Long.MAX_VALUE + " > 16"),
                Arguments.of(0L, 8L, 1L, destination + "9 > 8"),
                Arguments.of(0L, 9L, 0L, destination + "9 > 8"),
                Arguments.of(1L, 1L, 8L, destination + "9 > 8"),
                Arguments.of(0L, 1L << 31, 0L, destination + "2147483648 > 8"));
    }

    // Checks that the mapping strategy refuses a range past the end of either buffer before any bind or GL call
    @ParameterizedTest
    @MethodSource("pastTheEnd")
    void pixelPackRefusesRangesPastTheEndOfEitherBuffer(long readOffset, long writeOffset, long bytes, String message) {
        GlMutableBuffer src = buffer(16, 1);
        GlMutableBuffer dst = buffer(8, 2);
        refusedUntouched(IllegalArgumentException.class, message, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, readOffset, writeOffset, bytes));
    }

    // Checks that an empty range inside both buffers neither binds nor maps nor copies
    @Test
    void pixelPackSkipsEmptyRanges() {
        GlMutableBuffer empty = buffer(0, 1);
        GlMutableBuffer full = buffer(16, 2);
        GlBuffer[][] pairs = {{empty, empty}, {empty, full}, {full, empty}, {full, full}, {full, full}, {empty, full}};
        long[][] offsets = {{0L, 0L}, {0L, 16L}, {16L, 0L}, {8L, 8L}, {16L, 0L}, {0L, 0L}};
        for (int i = 0; i < pairs.length; i++) {
            Mockito.clearInvocations(gl, list);
            BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, pairs[i][0], pairs[i][1], offsets[i][0], offsets[i][1], 0L);
            Mockito.verifyNoInteractions(list, gl);
        }
        assertEquals(0, copies);
    }

    // Checks that the mapping strategy refuses a mapped buffer before any bind or GL call
    @Test
    void pixelPackRefusesMappedBuffersBeforeAnyCall() {
        GlMutableBuffer src = buffer(16, 1);
        GlMutableBuffer dst = buffer(16, 2);
        src.setActiveMapping(new GlBufferMapping(src, ByteBuffer.allocateDirect(16)));
        refusedUntouched(IllegalStateException.class, MAPPED, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 0L, 0L, 4L));
        refusedUntouched(IllegalStateException.class, MAPPED, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, dst, src, 0L, 0L, 4L));
        refusedUntouched(IllegalStateException.class, MAPPED, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, src, 0L, 8L, 4L));
        refusedUntouched(IllegalStateException.class, MAPPED, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 0L, 0L, 0L));
        // Reports the mapping before a range past the end
        refusedUntouched(IllegalStateException.class, MAPPED, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 0L, 0L, 64L));
    }

    // Checks that a failed source map maps nothing else and unbinds both pixel binding points
    @Test
    void pixelPackSourceMapFailureUnbindsBothPoints() {
        GlMutableBuffer src = buffer(16, 1);
        GlMutableBuffer dst = buffer(16, 2);
        byte[] before = bytes(dst);
        Mockito.doReturn(0L).when(gl).nglMapBuffer(PACK, READ_ONLY);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 0L, 0L, 8L));
        assertEquals("Source buffer could not be mapped", thrown.getMessage());
        Mockito.verify(gl, Mockito.never()).nglMapBuffer(Mockito.eq(UNPACK), anyInt());
        Mockito.verify(gl, Mockito.never()).glUnmapBuffer(anyInt());
        assertArrayEquals(before, bytes(dst));
        assertEquals(0, copies);
        assertPixelPointsUnbound();
    }

    // Checks that a failed destination map unmaps the source and unbinds both pixel binding points
    @Test
    void pixelPackDestinationMapFailureUnmapsTheSourceAndUnbindsBothPoints() {
        GlMutableBuffer src = buffer(16, 1);
        GlMutableBuffer dst = buffer(16, 2);
        byte[] before = bytes(dst);
        Mockito.doReturn(0L).when(gl).nglMapBuffer(UNPACK, WRITE_ONLY);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 0L, 0L, 8L));
        assertEquals("Destination buffer could not be mapped", thrown.getMessage());
        Mockito.verify(gl).glUnmapBuffer(PACK);
        Mockito.verify(gl, Mockito.never()).glUnmapBuffer(UNPACK);
        assertArrayEquals(before, bytes(dst));
        assertEquals(0, copies);
        assertPixelPointsUnbound();
    }

    // Checks that a failed map within one buffer reports the source and unbinds both pixel binding points
    @Test
    void pixelPackMapFailureWithinOneBufferUnbindsBothPoints() {
        GlMutableBuffer buffer = buffer(64, 3);
        byte[] before = bytes(buffer);
        Mockito.doReturn(0L).when(gl).nglMapBuffer(PACK, READ_WRITE);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, buffer, buffer, 0L, 32L, 16L));
        assertEquals("Source buffer could not be mapped", thrown.getMessage());
        Mockito.verify(gl, Mockito.never()).glUnmapBuffer(anyInt());
        assertArrayEquals(before, bytes(buffer));
        assertPixelPointsUnbound();
    }

    // Checks that a failed destination unmap still unmaps the source and unbinds both pixel binding points
    @Test
    void pixelPackFailedDestinationUnmapStillUnmapsTheSourceAndUnbinds() {
        GlMutableBuffer src = buffer(16, 1);
        GlMutableBuffer dst = buffer(16, 2);
        RuntimeException failure = new RuntimeException("Unmap failed");
        Mockito.doAnswer(call -> {
            unmap(UNPACK);
            throw failure;
        }).when(gl).glUnmapBuffer(UNPACK);
        assertSame(failure, assertThrows(RuntimeException.class, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 0L, 8L, 8L)));
        Mockito.verify(gl).glUnmapBuffer(PACK);
        assertArrayEquals(slice(bytes(src), 0, 8), slice(bytes(dst), 8, 8));
        assertPixelPointsUnbound();
    }

    // Checks that a failed source unmap still unmaps the destination and unbinds both pixel binding points
    @Test
    void pixelPackFailedSourceUnmapStillUnmapsTheDestinationAndUnbinds() {
        GlMutableBuffer src = buffer(16, 1);
        GlMutableBuffer dst = buffer(16, 2);
        RuntimeException failure = new RuntimeException("Unmap failed");
        Mockito.doAnswer(call -> {
            unmap(PACK);
            throw failure;
        }).when(gl).glUnmapBuffer(PACK);
        assertSame(failure, assertThrows(RuntimeException.class, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 0L, 8L, 8L)));
        Mockito.verify(gl).glUnmapBuffer(UNPACK);
        assertPixelPointsUnbound();
    }

    // Checks that a failed pack unbind still unbinds the unpack binding point
    @Test
    void pixelPackFailedPackUnbindStillUnbindsTheUnpackPoint() {
        GlMutableBuffer src = buffer(16, 1);
        GlMutableBuffer dst = buffer(16, 2);
        RuntimeException failure = new RuntimeException("Unbind failed");
        Mockito.doThrow(failure).when(list).bindBuffer(GlBufferTarget.PIXEL_PACK_BUFFER, null);
        assertSame(failure, assertThrows(RuntimeException.class, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 4L, 0L, 12L)));
        Mockito.verify(list).bindBuffer(GlBufferTarget.PIXEL_UNPACK_BUFFER, null);
        assertEquals(0, bindings.getOrDefault(UNPACK, 0));
        assertArrayEquals(slice(bytes(src), 4, 12), slice(bytes(dst), 0, 12));
    }

    // Checks that a failed unpack bind maps nothing and unbinds both pixel binding points
    @Test
    void pixelPackFailedUnpackBindMapsNothingAndUnbindsBothPoints() {
        GlMutableBuffer src = buffer(16, 1);
        GlMutableBuffer dst = buffer(16, 2);
        RuntimeException failure = new RuntimeException("Bind failed");
        Mockito.doThrow(failure).when(list).bindBuffer(GlBufferTarget.PIXEL_UNPACK_BUFFER, dst);
        assertSame(failure, assertThrows(RuntimeException.class, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 0L, 0L, 8L)));
        Mockito.verify(gl, Mockito.never()).nglMapBuffer(anyInt(), anyInt());
        assertEquals(0, copies);
        assertPixelPointsUnbound();
    }

    // Checks that repeated copies map and unmap both buffers every time and leave them unmapped
    @Test
    void pixelPackRepeatedCopiesMapAndUnmapEveryTime() {
        GlMutableBuffer src = buffer(32, 1);
        GlMutableBuffer dst = buffer(32, 2);
        byte[] source = bytes(src);
        byte[] expected = bytes(dst);
        long[][] ranges = {{0L, 0L, 8L}, {8L, 8L, 8L}, {0L, 16L, 16L}, {31L, 0L, 1L}};
        for (long[] range : ranges) {
            BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, range[0], range[1], range[2]);
            System.arraycopy(source, (int) range[0], expected, (int) range[1], (int) range[2]);
            assertArrayEquals(expected, bytes(dst));
            assertTrue(mapped.isEmpty());
            assertPixelPointsUnbound();
        }
        Mockito.verify(gl, Mockito.times(ranges.length)).nglMapBuffer(PACK, READ_ONLY);
        Mockito.verify(gl, Mockito.times(ranges.length)).nglMapBuffer(UNPACK, WRITE_ONLY);
        Mockito.verify(gl, Mockito.times(ranges.length)).glUnmapBuffer(PACK);
        Mockito.verify(gl, Mockito.times(ranges.length)).glUnmapBuffer(UNPACK);
        assertNull(src.getActiveMapping());
        assertNull(dst.getActiveMapping());
        assertEquals(ranges.length, copies);
    }

    // Checks that a buffer without a recorded size copies like a mutable buffer
    @Test
    void pixelPackCopiesFromAnImmutableBuffer() {
        GlImmutableBuffer src = new GlImmutableBuffer(EnumBitField.of(GlBufferStorageFlags.MAP_READ));
        store(src, 32L, 0L, 32, 4);
        GlMutableBuffer dst = buffer(16, 5);
        BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, 16L, 0L, 16L);
        assertArrayEquals(slice(bytes(src), 16, 16), bytes(dst));
        assertPixelPointsUnbound();
    }

    // Checks that offsets past the int limit reach the native copy without truncation
    @Test
    void pixelPackAddsOffsetsPastTheIntLimitWithoutTruncation() {
        GlMutableBuffer src = new GlMutableBuffer();
        src.setSize((1L << 32) + 16L);
        store(src, (1L << 32) + 16L, 1L << 32, 16, 6);
        GlMutableBuffer dst = new GlMutableBuffer();
        dst.setSize((1L << 31) + 16L);
        store(dst, (1L << 31) + 16L, 1L << 31, 16, 7);
        BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, (1L << 32) + 3L, (1L << 31) + 5L, 11L);
        assertArrayEquals(slice(bytes(src), 3, 11), slice(bytes(dst), 5, 11));
        assertEquals(1, copies);
    }

    // Checks that a range ending at the long limit passes the checks and reaches the mapping
    @Test
    void pixelPackAcceptsARangeEndingAtTheLongLimit() {
        GlMutableBuffer src = new GlMutableBuffer();
        src.setSize(Long.MAX_VALUE);
        GlMutableBuffer dst = buffer(8, 1);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> BufferCopyFunctions.PIXEL_PACK.copyBufferSubData(list, src, dst, Long.MAX_VALUE - 8L, 0L, 8L));
        assertEquals("Source buffer could not be mapped", thrown.getMessage());
        Mockito.verify(gl).nglMapBuffer(PACK, READ_ONLY);
        assertPixelPointsUnbound();
    }

    // Checks that the mapping strategy copies through a device command list before and after the list closes
    @Test
    void pixelPackCopiesThroughADeviceCommandListAfterItCloses() {
        CommandList deviceList = deviceList(true);
        assertSame(BufferCopyFunctions.PIXEL_PACK, device.getDeviceFunctions().bufferCopyFunctions());
        GlMutableBuffer src = deviceBuffer(deviceList, 32, 1);
        GlMutableBuffer dst = deviceBuffer(deviceList, 32, 2);
        byte[] source = bytes(src);
        byte[] expected = bytes(dst);
        deviceList.copyBufferSubData(src, dst, 4L, 8L, 12L);
        deviceList.close();
        deviceList.copyBufferSubData(src, dst, 20L, 24L, 8L);
        System.arraycopy(source, 4, expected, 8, 12);
        System.arraycopy(source, 20, expected, 24, 8);
        assertArrayEquals(expected, bytes(dst));
        Mockito.verify(gl, Mockito.times(2)).glBindBuffer(PACK, src.handle());
        Mockito.verify(gl, Mockito.times(2)).glBindBuffer(UNPACK, dst.handle());
        Mockito.verify(gl, Mockito.times(2)).glBindBuffer(PACK, 0);
        Mockito.verify(gl, Mockito.times(2)).glBindBuffer(UNPACK, 0);
        assertPixelPointsUnbound();
    }

    // Checks that the GPU strategy copies through a device command list before and after the list closes
    @Test
    void coreCopiesThroughADeviceCommandListAfterItCloses() {
        CommandList deviceList = deviceList(false);
        assertSame(BufferCopyFunctions.CORE, device.getDeviceFunctions().bufferCopyFunctions());
        GlMutableBuffer src = deviceBuffer(deviceList, 32, 1);
        GlMutableBuffer dst = deviceBuffer(deviceList, 32, 2);
        deviceList.copyBufferSubData(src, dst, 4L, 8L, 12L);
        deviceList.close();
        deviceList.copyBufferSubData(src, dst, 20L, 24L, 8L);
        Mockito.verify(gl).glBindBuffer(COPY_READ, src.handle());
        Mockito.verify(gl).glBindBuffer(COPY_WRITE, dst.handle());
        Mockito.verify(gl).glCopyBufferSubData(COPY_READ, COPY_WRITE, 4L, 8L, 12L);
        Mockito.verify(gl).glCopyBufferSubData(COPY_READ, COPY_WRITE, 20L, 24L, 8L);
        assertEquals(0, copies);
    }

    // Checks that the GPU strategy is picked when GL 3.1 or ARB_copy_buffer is present and the mapping strategy otherwise
    @ParameterizedTest
    @CsvSource({"true, true, CORE", "true, false, CORE", "false, true, CORE", "false, false, PIXEL_PACK"})
    void pickBestFollowsTheCopyCapabilities(boolean version, boolean extension, BufferCopyFunctions expected) {
        // Forgets the choice that the shared device makes when the interface first loads
        RenderDevice renderDevice = Mockito.mock(RenderDevice.class);
        Mockito.clearInvocations(gl);
        Mockito.doReturn(version).when(gl).isOpenGLVersionSupported(3, 1);
        Mockito.doReturn(extension).when(gl).isExtensionSupported(GLExtension.ARB_copy_buffer);
        assertSame(expected, BufferCopyFunctions.pickBest(renderDevice));
        Mockito.verify(gl).isOpenGLVersionSupported(3, 1);
        Mockito.verify(gl, Mockito.times(version ? 0 : 1)).isExtensionSupported(GLExtension.ARB_copy_buffer);
        Mockito.verifyNoMoreInteractions(gl);
        Mockito.verifyNoInteractions(renderDevice);
    }

    // Checks that the choice ignores a null device and reads the capabilities again on every call
    @Test
    void pickBestIgnoresTheDeviceAndReadsTheCapabilitiesEveryCall() {
        assertSame(BufferCopyFunctions.CORE, BufferCopyFunctions.pickBest(null));
        Mockito.doReturn(false).when(gl).isOpenGLVersionSupported(anyInt(), anyInt());
        Mockito.doReturn(false).when(gl).isExtensionSupported(any());
        assertSame(BufferCopyFunctions.PIXEL_PACK, BufferCopyFunctions.pickBest(null));
        assertSame(BufferCopyFunctions.PIXEL_PACK, BufferCopyFunctions.pickBest(null));
        Mockito.doReturn(true).when(gl).isExtensionSupported(GLExtension.ARB_copy_buffer);
        assertSame(BufferCopyFunctions.CORE, BufferCopyFunctions.pickBest(null));
        Mockito.verify(gl, Mockito.times(4)).isOpenGLVersionSupported(3, 1);
    }

    // Checks the declared strategies and their lookup by name
    @Test
    void strategiesKeepTheirOrderAndNames() {
        assertArrayEquals(new BufferCopyFunctions[] {BufferCopyFunctions.CORE, BufferCopyFunctions.PIXEL_PACK}, BufferCopyFunctions.values());
        assertNotSame(BufferCopyFunctions.values(), BufferCopyFunctions.values());
        assertSame(BufferCopyFunctions.CORE, BufferCopyFunctions.valueOf("CORE"));
        assertSame(BufferCopyFunctions.PIXEL_PACK, BufferCopyFunctions.valueOf("PIXEL_PACK"));
        assertThrows(IllegalArgumentException.class, () -> BufferCopyFunctions.valueOf("core"));
        assertThrows(IllegalArgumentException.class, () -> BufferCopyFunctions.valueOf(""));
        assertThrows(NullPointerException.class, () -> BufferCopyFunctions.valueOf(null));
        for (BufferCopyFunctions copy : BufferCopyFunctions.values()) {
            assertSame(BufferCopyFunctions.class, copy.getDeclaringClass());
        }
    }

    // Asserts that the call throws the type with the message and touches neither the command list nor GL nor native memory
    private <T extends Throwable> void refusedUntouched(Class<T> type, String message, Executable call) {
        Mockito.clearInvocations(gl, list);
        int before = copies;
        T thrown = assertThrows(type, call);
        assertEquals(message, thrown.getMessage());
        Mockito.verifyNoInteractions(list, gl);
        assertEquals(before, copies);
    }

    // Asserts that both pixel binding points hold buffer zero
    private void assertPixelPointsUnbound() {
        assertEquals(0, bindings.getOrDefault(PACK, 0));
        assertEquals(0, bindings.getOrDefault(UNPACK, 0));
    }

    // Returns an active render device command list that picks the mapping strategy when legacy is set
    private CommandList deviceList(boolean legacy) {
        Mockito.doReturn(!legacy).when(gl).isOpenGLVersionSupported(anyInt(), anyInt());
        Mockito.doReturn(!legacy).when(gl).isExtensionSupported(any());
        device = new GLRenderDevice();
        device.makeActive();
        return device.createCommandList();
    }

    // Creates a buffer through the device command list with storage of the size whose bytes follow the seed
    private GlMutableBuffer deviceBuffer(CommandList deviceList, int size, int seed) {
        GlMutableBuffer buffer = deviceList.createMutableBuffer();
        deviceList.allocateStorage(buffer, size, GlBufferUsage.STATIC_DRAW);
        store(buffer, size, 0L, size, seed);
        return buffer;
    }

    // Creates a mutable buffer with storage of the size whose bytes follow the seed
    private GlMutableBuffer buffer(int size, int seed) {
        GlMutableBuffer buffer = new GlMutableBuffer();
        buffer.setSize(size);
        store(buffer, size, 0L, size, seed);
        return buffer;
    }

    // Backs the bytes of the buffer from the window offset with guarded native memory whose bytes follow the seed
    private void store(GlBuffer buffer, long size, long window, int backed, int seed) {
        long raw = MemoryUtil.nmemAlloc(backed + 2L * GUARD);
        MemoryUtil.memSet(raw, GUARD_BYTE, backed + 2L * GUARD);
        for (int i = 0; i < backed; i++) {
            MemoryUtil.memPutByte(raw + GUARD + i, (byte) (seed * 61 + i * 7 + 1));
        }
        blocks.put(buffer.handle(), new Block(raw, size, window, backed));
    }

    // Returns a copy of the backed bytes of the buffer
    private byte[] bytes(GlBuffer buffer) {
        Block block = blocks.get(buffer.handle());
        byte[] bytes = new byte[block.backed()];
        MemoryUtil.memByteBuffer(block.first(), block.backed()).get(bytes);
        return bytes;
    }

    // Returns the given number of bytes of the array from the offset
    private static byte[] slice(byte[] bytes, int offset, int length) {
        byte[] slice = new byte[length];
        System.arraycopy(bytes, offset, slice, 0, length);
        return slice;
    }

    // Fails when a guard byte of the block was written
    private static void assertGuardsIntact(int name, Block block) {
        for (int i = 0; i < GUARD; i++) {
            assertEquals(GUARD_BYTE, MemoryUtil.memGetByte(block.raw() + i), "Guard byte " + i + " before buffer " + name);
            assertEquals(GUARD_BYTE, MemoryUtil.memGetByte(block.first() + block.backed() + i), "Guard byte " + i + " after buffer " + name);
        }
    }

    // Forwards a bind of the command list mock to the GL mock as the device does
    private Object forwardBind(InvocationOnMock call) {
        GlBufferTarget target = call.getArgument(0);
        GlBuffer buffer = call.getArgument(1);
        gl.glBindBuffer(target.getTargetParameter(), buffer == null ? 0 : buffer.handle());
        return null;
    }

    // Returns the mapping address of the buffer bound to the target or the null address for no buffer or empty storage or a buffer that is already mapped
    private long map(int target) {
        int name = bindings.getOrDefault(target, 0);
        Block block = blocks.get(name);
        if (block == null || block.size() == 0L || !mapped.add(name)) {
            return MemoryUtil.NULL;
        }
        return block.address();
    }

    // Unmaps the buffer bound to the target
    private void unmap(int target) {
        mapped.remove(bindings.getOrDefault(target, 0));
    }

    // Checks a native copy against the open mappings before it runs and runs every other MemoryUtil call unchanged
    private Object memoryCall(InvocationOnMock call) throws Throwable {
        Method method = call.getMethod();
        if (method.getName().equals("memCopy") && method.getParameterCount() == 3 && method.getParameterTypes()[0] == long.class) {
            checkCopy(call.getArgument(0), call.getArgument(1), call.getArgument(2));
        }
        return call.callRealMethod();
    }

    // Fails a native copy with a negative size or a range outside the open mappings before it touches memory
    private void checkCopy(long src, long dst, long bytes) {
        copies++;
        if (bytes < 0L || !insideMapping(src, bytes) || !insideMapping(dst, bytes)) {
            throw new AssertionError("Native copy of " + bytes + " bytes outside the open mappings");
        }
    }

    // Returns whether the address range lies inside the backed storage of a mapped buffer
    private boolean insideMapping(long address, long bytes) {
        for (int name : mapped) {
            Block block = blocks.get(name);
            long first = block.first();
            if (address >= first && address - first <= block.backed() - bytes) {
                return true;
            }
        }
        return false;
    }

    // Native storage of one buffer of the recorded size whose bytes from the window offset are backed between two guard zones
    private record Block(long raw, long size, long window, int backed) {
        // Returns the address of the first backed byte
        long first() {
            return raw + GUARD;
        }

        // Returns the address that a mapping of the whole buffer hands out
        long address() {
            return raw + GUARD - window;
        }
    }
}
