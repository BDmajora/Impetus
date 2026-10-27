package com.bdmajora.impetus.engine.impl.gl.arena;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.arena.staging.StagingBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.system.MemoryUtil;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests of every member of the pending upload alone and inside a real arena
class PendingUploadTest {
    // Message of an upload that holds no result
    private static final String NOT_COMPUTED = "Result not computed";
    // Message of a second result
    private static final String ALREADY_PROVIDED = "Result already provided";
    // Message of a null result
    private static final String NULL_RESULT = "Result must not be null";
    // Message of a native buffer that was freed
    private static final String BUFFER_DELETED = "Buffer has been deleted";

    // Live native buffers that are freed after each test
    private final List<NativeBuffer> natives = new ArrayList<>();
    // Command list mock that hands out a fresh buffer on every create
    private CommandList commands;
    // Staging buffer mock that drops every copy
    private StagingBuffer staging;

    // Creates the command list and staging buffer mocks
    @BeforeEach
    void createMocks() {
        commands = Mockito.mock(CommandList.class);
        Mockito.when(commands.createMutableBuffer()).thenAnswer(invocation -> new GlMutableBuffer());
        staging = Mockito.mock(StagingBuffer.class);
    }

    // Frees the native buffers that the test left live
    @AfterEach
    void freeNatives() {
        for (NativeBuffer buffer : natives) {
            buffer.free();
        }
        natives.clear();
    }

    // Allocates a native buffer of the given capacity that is freed after the test
    private NativeBuffer buffer(int capacity) {
        NativeBuffer buffer = new NativeBuffer(capacity);
        natives.add(buffer);
        return buffer;
    }

    // Frees a native buffer of the test before the test ends
    private void release(NativeBuffer buffer) {
        assertTrue(natives.remove(buffer));
        buffer.free();
    }

    // Creates an upload of a fresh native buffer of the given capacity
    private PendingUpload upload(int capacity) {
        return PendingUpload.of(buffer(capacity));
    }

    // Creates a buffer mock that reports the given capacity and holds no memory
    private static NativeBuffer claiming(int length) {
        NativeBuffer data = Mockito.mock(NativeBuffer.class);
        Mockito.when(data.getLength()).thenReturn(length);
        return data;
    }

    // Creates a segment that belongs to no arena
    private static GlBufferSegment segment(int offset, int length) {
        return new GlBufferSegment(null, offset, length);
    }

    // Creates an arena of four byte elements over the mocks
    private GlBufferArena arena() {
        return new GlBufferArena(commands, 4, 0, staging);
    }

    // Returns a mutable queue of the uploads
    private static List<PendingUpload> queue(PendingUpload... uploads) {
        return new ArrayList<>(List.of(uploads));
    }

    // Asserts that the upload holds no result
    private static void assertUnplaced(PendingUpload upload) {
        assertEquals(NOT_COMPUTED, assertThrows(IllegalStateException.class, upload::getResult).getMessage());
    }

    // Checks that a null buffer gives a null upload
    @Test
    void ofReturnsNullForANullBuffer() {
        assertNull(PendingUpload.of(null));
    }

    // Checks that the upload wraps the given buffer and holds no result
    @Test
    void ofWrapsTheBufferWithoutAResult() {
        NativeBuffer data = buffer(16);

        PendingUpload upload = PendingUpload.of(data);

        assertSame(data, upload.getDataBuffer());
        assertEquals(16, upload.getLength());
        assertUnplaced(upload);
    }

    // Checks that each call creates a separate upload with its own result
    @Test
    void ofReturnsADistinctUploadOnEveryCall() {
        NativeBuffer data = buffer(8);

        PendingUpload first = PendingUpload.of(data);
        PendingUpload second = PendingUpload.of(data);
        first.setResult(segment(0, 2));

        assertNotSame(first, second);
        assertSame(data, second.getDataBuffer());
        assertUnplaced(second);
    }

    // Checks that creating and reading uploads leaves the native byte total unchanged and the buffer still owned by the test
    @Test
    void ofNeitherAllocatesNorFreesNativeMemory() {
        NativeBuffer data = new NativeBuffer(32);
        long before = NativeBuffer.getTotalAllocated();

        for (int i = 0; i < 100; i++) {
            PendingUpload upload = PendingUpload.of(data);
            upload.setResult(segment(i, 8));
            assertEquals(32, upload.getLength());
            assertSame(data, upload.getDataBuffer());
        }

        assertEquals(before, NativeBuffer.getTotalAllocated());
        data.free();
        assertEquals(before - 32, NativeBuffer.getTotalAllocated());
    }

    // Checks that the upload exposes the same native memory as the buffer
    @Test
    void ofKeepsTheNativeMemoryOfTheBuffer() {
        NativeBuffer data = buffer(5);
        data.getDirectBuffer().put(0, new byte[] {1, 2, 3, 4, 5});

        ByteBuffer view = PendingUpload.of(data).getDataBuffer().getDirectBuffer();
        byte[] bytes = new byte[5];
        view.get(0, bytes);

        assertEquals(MemoryUtil.memAddress(data.getDirectBuffer()), MemoryUtil.memAddress(view));
        assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, bytes);
    }

    // Checks that a freed buffer still gives an upload whose data refuses to be read
    @Test
    void ofAcceptsAFreedBuffer() {
        NativeBuffer data = buffer(24);
        release(data);

        PendingUpload upload = PendingUpload.of(data);

        assertSame(data, upload.getDataBuffer());
        assertEquals(24, upload.getLength());
        assertEquals(BUFFER_DELETED, assertThrows(IllegalStateException.class, () -> upload.getDataBuffer().getDirectBuffer()).getMessage());
        assertUnplaced(upload);
    }

    // Checks that the capacity of the buffer is read once at creation
    @Test
    void ofReadsTheCapacityOnce() {
        NativeBuffer data = claiming(64);
        PendingUpload upload = PendingUpload.of(data);

        for (int i = 0; i < 1000; i++) {
            assertEquals(64, upload.getLength());
        }

        Mockito.verify(data).getLength();
        Mockito.verifyNoMoreInteractions(data);
    }

    // Checks that a negative capacity fails in the buffer before any upload exists
    @ParameterizedTest
    @ValueSource(ints = {-1, -4096, Integer.MIN_VALUE})
    void negativeCapacityNeverReachesAnUpload(int capacity) {
        assertThrows(IllegalArgumentException.class, () -> PendingUpload.of(new NativeBuffer(capacity)));
    }

    // Checks that repeated reads return the same buffer before and after placement
    @Test
    void getDataBufferReturnsTheSameBufferOnRepeatedCalls() {
        NativeBuffer data = buffer(4);
        PendingUpload upload = PendingUpload.of(data);

        assertSame(data, upload.getDataBuffer());
        assertSame(data, upload.getDataBuffer());
        upload.setResult(segment(0, 1));
        assertSame(data, upload.getDataBuffer());
    }

    // Checks that the buffer stays reachable through the upload after it is freed
    @Test
    void getDataBufferReturnsTheBufferAfterItIsFreed() {
        NativeBuffer data = buffer(12);
        PendingUpload upload = PendingUpload.of(data);

        release(data);

        assertSame(data, upload.getDataBuffer());
        assertThrows(IllegalStateException.class, data::free);
    }

    // Checks that the length equals the capacity in bytes on the byte and word and page boundaries
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 7, 8, 9, 15, 16, 17, 4095, 4096, 4097, 1 << 20})
    void getLengthReportsTheCapacityInBytes(int capacity) {
        PendingUpload upload = upload(capacity);

        assertEquals(capacity, upload.getLength());
        assertEquals(capacity, upload.getDataBuffer().getDirectBuffer().capacity());
    }

    // Checks that the length reaches the int limit without truncation
    @ParameterizedTest
    @ValueSource(ints = {1 << 30, Integer.MAX_VALUE - 1, Integer.MAX_VALUE})
    void getLengthReportsTheIntLimits(int capacity) {
        assertEquals(capacity, PendingUpload.of(claiming(capacity)).getLength());
    }

    // Checks that the length stays the same after the buffer is freed and after placement
    @Test
    void getLengthKeepsTheCapacityAfterFreeAndPlacement() {
        NativeBuffer data = buffer(40);
        PendingUpload upload = PendingUpload.of(data);

        upload.setResult(segment(3, 10));
        release(data);

        assertEquals(40, upload.getLength());
        assertEquals(40, upload.getLength());
    }

    // Checks that an upload without a result keeps refusing every read
    @Test
    void getResultRejectsAnUploadThatWasNotPlaced() {
        PendingUpload upload = upload(4);

        for (int i = 0; i < 3; i++) {
            assertUnplaced(upload);
        }
    }

    // Checks that the stored segment comes back on every read
    @Test
    void setResultStoresTheSegment() {
        PendingUpload upload = upload(4);
        GlBufferSegment segment = segment(0, 1);

        upload.setResult(segment);

        assertSame(segment, upload.getResult());
        assertSame(segment, upload.getResult());
    }

    // Checks that a second segment is refused and the first one stays
    @Test
    void setResultRejectsASecondResult() {
        PendingUpload upload = upload(4);
        GlBufferSegment first = segment(0, 1);
        upload.setResult(first);

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> upload.setResult(segment(1, 1)));

        assertEquals(ALREADY_PROVIDED, failure.getMessage());
        assertSame(first, upload.getResult());
    }

    // Checks that storing the same segment twice is refused
    @Test
    void setResultRejectsTheSameSegmentTwice() {
        PendingUpload upload = upload(4);
        GlBufferSegment segment = segment(0, 1);
        upload.setResult(segment);

        assertEquals(ALREADY_PROVIDED, assertThrows(IllegalStateException.class, () -> upload.setResult(segment)).getMessage());
        assertSame(segment, upload.getResult());
    }

    // Checks that a null segment is refused on every call and a later segment is still accepted
    @Test
    void setResultRejectsNull() {
        PendingUpload upload = upload(4);

        for (int i = 0; i < 3; i++) {
            assertEquals(NULL_RESULT, assertThrows(NullPointerException.class, () -> upload.setResult(null)).getMessage());
            assertUnplaced(upload);
        }

        GlBufferSegment segment = segment(0, 1);
        upload.setResult(segment);
        assertSame(segment, upload.getResult());
    }

    // Checks that a null segment after a result is refused and the result stays
    @Test
    void setResultRejectsNullAfterAResult() {
        PendingUpload upload = upload(4);
        GlBufferSegment segment = segment(0, 1);
        upload.setResult(segment);

        assertEquals(NULL_RESULT, assertThrows(NullPointerException.class, () -> upload.setResult(null)).getMessage());
        assertSame(segment, upload.getResult());
    }

    // Checks that segments at the int limits and at misaligned offsets come back unchanged
    @ParameterizedTest
    @CsvSource({"0, 0", "0, 1", "1, 3", "3, 5", "7, 9", "2147483646, 1", "2147483647, 0", "0, 2147483647", "1073741824, 1073741823"})
    void getResultKeepsBoundarySegments(int offset, int length) {
        PendingUpload upload = upload(1);
        GlBufferSegment segment = segment(offset, length);

        upload.setResult(segment);

        assertSame(segment, upload.getResult());
        assertEquals(offset, upload.getResult().getOffset());
        assertEquals(length, upload.getResult().getLength());
    }

    // Checks that the result stays after the buffer is freed
    @Test
    void getResultSurvivesFreeingTheData() {
        NativeBuffer data = buffer(8);
        PendingUpload upload = PendingUpload.of(data);
        GlBufferSegment segment = segment(2, 2);
        upload.setResult(segment);

        release(data);

        assertSame(segment, upload.getResult());
    }

    // Checks that the result of one upload never shows in another upload of the same buffer
    @Test
    void resultsOfTwoUploadsStayApart() {
        NativeBuffer data = buffer(8);
        PendingUpload a = PendingUpload.of(data);
        PendingUpload b = PendingUpload.of(data);
        GlBufferSegment first = segment(0, 2);
        GlBufferSegment second = segment(2, 2);

        a.setResult(first);
        assertUnplaced(b);
        b.setResult(second);

        assertSame(first, a.getResult());
        assertSame(second, b.getResult());
    }

    // Checks that the arena stores a whole element segment while the length stays in bytes
    @Test
    void arenaPlacementSetsTheResult() {
        GlBufferArena arena = arena();
        PendingUpload odd = upload(7);
        PendingUpload single = upload(1);
        PendingUpload empty = upload(0);
        PendingUpload whole = upload(8);
        List<PendingUpload> pending = queue(odd, single, empty, whole);

        arena.upload(commands, pending);

        assertTrue(pending.isEmpty());
        assertEquals(2, odd.getResult().getLength());
        assertEquals(1, single.getResult().getLength());
        assertEquals(0, empty.getResult().getLength());
        assertEquals(2, whole.getResult().getLength());
        assertEquals(7, odd.getLength());
        assertEquals(1, single.getLength());
        assertEquals(0, empty.getLength());
        assertEquals(8, whole.getLength());
    }

    // Checks that placing an upload a second time fails and keeps the first segment
    @Test
    void arenaRejectsAnUploadThatIsAlreadyPlaced() {
        GlBufferArena arena = arena();
        PendingUpload upload = upload(8);
        arena.upload(commands, queue(upload));
        GlBufferSegment first = upload.getResult();
        List<PendingUpload> again = queue(upload);

        assertEquals(ALREADY_PROVIDED, assertThrows(IllegalStateException.class, () -> arena.upload(commands, again)).getMessage());

        assertSame(first, upload.getResult());
        assertEquals(List.of(upload), again);
    }

    // Checks that the arena refuses an upload of a freed buffer and stores no result
    @Test
    void arenaRejectsAnUploadOfAFreedBuffer() {
        GlBufferArena arena = arena();
        NativeBuffer data = buffer(8);
        PendingUpload upload = PendingUpload.of(data);
        release(data);
        List<PendingUpload> pending = queue(upload);

        assertEquals(BUFFER_DELETED, assertThrows(IllegalStateException.class, () -> arena.upload(commands, pending)).getMessage());

        assertUnplaced(upload);
        assertEquals(List.of(upload), pending);
    }

    // Checks that the result stays after its segment returns to the arena
    @Test
    void getResultSurvivesDeletingTheSegment() {
        GlBufferArena arena = arena();
        PendingUpload upload = upload(16);
        arena.upload(commands, queue(upload));
        GlBufferSegment segment = upload.getResult();

        segment.delete();

        assertSame(segment, upload.getResult());
        assertTrue(arena.isEmpty());
    }

    // Checks that the class stays final with a private constructor and the same public members
    @Test
    void classKeepsItsShape() throws ReflectiveOperationException {
        assertTrue(Modifier.isPublic(PendingUpload.class.getModifiers()));
        assertTrue(Modifier.isFinal(PendingUpload.class.getModifiers()));

        Constructor<?>[] constructors = PendingUpload.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        assertArrayEquals(new Class<?>[] {NativeBuffer.class}, constructors[0].getParameterTypes());

        Map<String, String> members = new TreeMap<>();
        for (Method method : PendingUpload.class.getDeclaredMethods()) {
            if (!method.isSynthetic() && !Modifier.isPrivate(method.getModifiers())) {
                members.put(method.getName(), Modifier.toString(method.getModifiers()) + " " + method.getReturnType().getSimpleName());
            }
        }

        assertEquals(Map.of(
                "of", "public static PendingUpload",
                "getDataBuffer", "public NativeBuffer",
                "setResult", "protected void",
                "getResult", "public GlBufferSegment",
                "getLength", "public int"), members);
        assertEquals(PendingUpload.class, PendingUpload.class.getMethod("of", NativeBuffer.class).getReturnType());
        assertEquals(void.class, PendingUpload.class.getDeclaredMethod("setResult", GlBufferSegment.class).getReturnType());
    }
}
