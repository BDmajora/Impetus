package com.bdmajora.impetus.engine.impl.gl.arena;

import com.bdmajora.impetus.engine.impl.common.util.NativeBuffer;
import com.bdmajora.impetus.engine.impl.gl.arena.staging.StagingBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;

// Tests of the buffer segment alone and inside a real arena of single byte elements
class GlBufferSegmentTest {
    // Message of a range that ends beyond the int range
    private static final String END_BEYOND_RANGE = "Segment end must not exceed 2147483647";
    // Message of a negative offset
    private static final String NEGATIVE_OFFSET = "Offset must not be negative";
    // Message of a length below one in a setter
    private static final String LENGTH_BELOW_ONE = "Length must be positive";
    // Handle of the private segment list check of the arena
    private static final MethodHandle CHECK;

    // Resolves the private segment list check of the arena
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(GlBufferArena.class, MethodHandles.lookup());
            CHECK = lookup.findVirtual(GlBufferArena.class, "checkAssertions0", MethodType.methodType(void.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Native buffers freed after each test
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

    // Frees the native buffers of the test
    @AfterEach
    void freeNatives() {
        for (NativeBuffer buffer : natives) {
            buffer.free();
        }
        natives.clear();
    }

    // Creates an arena of single byte elements over the mocks
    private GlBufferArena arena() {
        return new GlBufferArena(commands, 1, 0, staging);
    }

    // Places uploads of the given byte lengths in one batch and returns their segments in order
    private GlBufferSegment[] place(GlBufferArena arena, int... lengths) {
        List<PendingUpload> uploads = new ArrayList<>();
        for (int length : lengths) {
            NativeBuffer data = new NativeBuffer(length);
            natives.add(data);
            uploads.add(PendingUpload.of(data));
        }
        List<PendingUpload> queue = new ArrayList<>(uploads);
        arena.upload(commands, queue);
        assertTrue(queue.isEmpty());
        GlBufferSegment[] segments = new GlBufferSegment[lengths.length];
        for (int i = 0; i < segments.length; i++) {
            segments[i] = uploads.get(i).getResult();
        }
        return segments;
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

    // Links the segments both ways in the given order
    private static void link(GlBufferSegment... segments) {
        for (int i = 1; i < segments.length; i++) {
            segments[i - 1].setNext(segments[i]);
            segments[i].setPrev(segments[i - 1]);
        }
    }

    // Asserts the offset and length and end of a segment
    private static void assertRange(GlBufferSegment segment, int offset, int length) {
        assertEquals(offset, segment.getOffset());
        assertEquals(length, segment.getLength());
        assertEquals(offset + length, segment.getEnd());
    }

    // Asserts both links of a segment
    private static void assertLinks(GlBufferSegment segment, GlBufferSegment prev, GlBufferSegment next) {
        assertSame(prev, segment.getPrev());
        assertSame(next, segment.getNext());
    }

    // Verifies that the constructor stores the range and creates a live unlinked segment
    @ParameterizedTest
    @CsvSource({"0, 0", "0, 1", "1, 0", "3, 5", "7, 1", "0, 2147483647", "2147483647, 0", "2147483646, 1", "1073741824, 1073741823"})
    void constructorStoresTheRange(int offset, int length) {
        GlBufferSegment segment = new GlBufferSegment(Mockito.mock(GlBufferArena.class), offset, length);
        assertRange(segment, offset, length);
        assertFalse(segment.isFree());
        assertLinks(segment, null, null);
    }

    // Verifies that the constructor accepts a null arena for a search key
    @Test
    void constructorAcceptsANullArena() {
        GlBufferSegment segment = new GlBufferSegment(null, 0, 1);
        assertRange(segment, 0, 1);
        assertFalse(segment.isFree());
        assertLinks(segment, null, null);
    }

    // Verifies that the constructor rejects a negative offset or length and an end beyond the int range
    @ParameterizedTest
    @CsvSource({
            "-1, 0, Offset must not be negative",
            "-2147483648, 0, Offset must not be negative",
            "-1, -1, Offset must not be negative",
            "-1, 1, Offset must not be negative",
            "-2147483648, 2147483647, Offset must not be negative",
            "0, -1, Length must not be negative",
            "0, -2147483648, Length must not be negative",
            "1, -1, Length must not be negative",
            "2147483647, -2147483648, Length must not be negative",
            "2147483647, 1, Segment end must not exceed 2147483647",
            "1, 2147483647, Segment end must not exceed 2147483647",
            "2147483647, 2147483647, Segment end must not exceed 2147483647",
            "1073741824, 1073741824, Segment end must not exceed 2147483647"
    })
    void constructorRejectsAnInvalidRange(int offset, int length, String message) {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> new GlBufferSegment(null, offset, length));
        assertEquals(message, thrown.getMessage());
    }

    // Verifies that the end follows every change of the offset and the length
    @Test
    void endFollowsTheOffsetAndTheLength() {
        GlBufferSegment segment = new GlBufferSegment(null, 0, 0);
        assertEquals(0, segment.getEnd());
        segment.setOffset(9);
        assertEquals(9, segment.getEnd());
        segment.setLength(4);
        assertEquals(13, segment.getEnd());
        segment.setOffset(2);
        assertEquals(6, segment.getEnd());
        assertEquals(6, segment.getEnd());
    }

    // Verifies that setLength stores every positive length that keeps the end in the int range
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 7, 1 << 20, Integer.MAX_VALUE - 1, Integer.MAX_VALUE})
    void setLengthStoresAPositiveLength(int length) {
        GlBufferSegment segment = new GlBufferSegment(null, 0, 3);
        segment.setLength(length);
        assertRange(segment, 0, length);
    }

    // Verifies that setLength rejects a length below one and keeps the stored length
    @ParameterizedTest
    @ValueSource(ints = {0, -1, -4, Integer.MIN_VALUE})
    void setLengthRejectsALengthBelowOne(int length) {
        GlBufferSegment segment = new GlBufferSegment(null, 2, 4);
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> segment.setLength(length));
        assertEquals(LENGTH_BELOW_ONE, thrown.getMessage());
        assertRange(segment, 2, 4);
    }

    // Verifies that setLength rejects a length that ends the segment beyond the int range
    @Test
    void setLengthRejectsAnEndBeyondTheIntRange() {
        GlBufferSegment segment = new GlBufferSegment(null, 10, 5);
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> segment.setLength(Integer.MAX_VALUE - 9));
        assertEquals(END_BEYOND_RANGE, thrown.getMessage());
        assertThrows(IllegalArgumentException.class, () -> segment.setLength(Integer.MAX_VALUE));
        assertRange(segment, 10, 5);
        segment.setLength(Integer.MAX_VALUE - 10);
        assertRange(segment, 10, Integer.MAX_VALUE - 10);
        GlBufferSegment last = new GlBufferSegment(null, Integer.MAX_VALUE, 0);
        thrown = assertThrows(IllegalArgumentException.class, () -> last.setLength(1));
        assertEquals(END_BEYOND_RANGE, thrown.getMessage());
        assertRange(last, Integer.MAX_VALUE, 0);
    }

    // Verifies that setLength gives a zero length segment elements
    @Test
    void setLengthGrowsAZeroLengthSegment() {
        GlBufferSegment segment = new GlBufferSegment(null, 4, 0);
        segment.setLength(3);
        assertRange(segment, 4, 3);
    }

    // Verifies that repeated setLength calls keep the last length
    @Test
    void setLengthKeepsTheLastOfRepeatedCalls() {
        GlBufferSegment segment = new GlBufferSegment(null, 1, 1);
        segment.setLength(5);
        segment.setLength(5);
        assertRange(segment, 1, 5);
        segment.setLength(2);
        assertRange(segment, 1, 2);
    }

    // Verifies that setOffset stores every offset that keeps the end in the int range
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 1023, Integer.MAX_VALUE - 9, Integer.MAX_VALUE - 8})
    void setOffsetStoresTheOffset(int offset) {
        GlBufferSegment segment = new GlBufferSegment(null, 5, 8);
        segment.setOffset(offset);
        assertRange(segment, offset, 8);
    }

    // Verifies that setOffset rejects a negative offset and keeps the stored offset
    @ParameterizedTest
    @ValueSource(ints = {-1, -8, -9, Integer.MIN_VALUE})
    void setOffsetRejectsANegativeOffset(int offset) {
        GlBufferSegment segment = new GlBufferSegment(null, 5, 8);
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> segment.setOffset(offset));
        assertEquals(NEGATIVE_OFFSET, thrown.getMessage());
        assertRange(segment, 5, 8);
    }

    // Verifies that setOffset rejects an offset that ends the segment beyond the int range
    @Test
    void setOffsetRejectsAnEndBeyondTheIntRange() {
        GlBufferSegment segment = new GlBufferSegment(null, 0, 8);
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> segment.setOffset(Integer.MAX_VALUE - 7));
        assertEquals(END_BEYOND_RANGE, thrown.getMessage());
        assertThrows(IllegalArgumentException.class, () -> segment.setOffset(Integer.MAX_VALUE));
        assertRange(segment, 0, 8);
        GlBufferSegment empty = new GlBufferSegment(null, 0, 0);
        empty.setOffset(Integer.MAX_VALUE);
        assertRange(empty, Integer.MAX_VALUE, 0);
        GlBufferSegment whole = new GlBufferSegment(null, 0, Integer.MAX_VALUE);
        thrown = assertThrows(IllegalArgumentException.class, () -> whole.setOffset(1));
        assertEquals(END_BEYOND_RANGE, thrown.getMessage());
        whole.setOffset(0);
        assertRange(whole, 0, Integer.MAX_VALUE);
    }

    // Verifies that repeated setOffset calls keep the last offset
    @Test
    void setOffsetKeepsTheLastOfRepeatedCalls() {
        GlBufferSegment segment = new GlBufferSegment(null, 0, 2);
        segment.setOffset(4);
        segment.setOffset(4);
        assertRange(segment, 4, 2);
        segment.setOffset(9);
        assertRange(segment, 9, 2);
    }

    // Verifies that the free flag starts clear and keeps the last stored value
    @Test
    void freeFlagStartsClearAndKeepsTheLastValue() {
        GlBufferSegment segment = new GlBufferSegment(null, 0, 1);
        assertFalse(segment.isFree());
        segment.setFree(true);
        assertTrue(segment.isFree());
        segment.setFree(true);
        assertTrue(segment.isFree());
        segment.setFree(false);
        assertFalse(segment.isFree());
        segment.setFree(false);
        assertFalse(segment.isFree());
    }

    // Verifies that a free segment keeps its range and links and still accepts new ones
    @Test
    void freeFlagLeavesTheRangeAndLinksAlone() {
        GlBufferSegment prev = new GlBufferSegment(null, 0, 2);
        GlBufferSegment segment = new GlBufferSegment(null, 2, 3);
        GlBufferSegment next = new GlBufferSegment(null, 5, 1);
        link(prev, segment, next);
        segment.setFree(true);
        assertRange(segment, 2, 3);
        assertLinks(segment, prev, next);
        segment.setLength(4);
        segment.setOffset(1);
        segment.setNext(null);
        segment.setPrev(null);
        assertRange(segment, 1, 4);
        assertLinks(segment, null, null);
        assertTrue(segment.isFree());
    }

    // Verifies that setNext stores the forward link alone and keeps the last value
    @Test
    void setNextStoresOnlyTheForwardLink() {
        GlBufferSegment a = new GlBufferSegment(null, 0, 1);
        GlBufferSegment b = new GlBufferSegment(null, 1, 1);
        GlBufferSegment c = new GlBufferSegment(null, 2, 1);
        a.setNext(b);
        assertLinks(a, null, b);
        assertLinks(b, null, null);
        a.setNext(b);
        assertLinks(a, null, b);
        a.setNext(c);
        assertLinks(a, null, c);
        assertLinks(c, null, null);
        a.setNext(null);
        assertLinks(a, null, null);
    }

    // Verifies that setPrev stores the back link alone and keeps the last value
    @Test
    void setPrevStoresOnlyTheBackLink() {
        GlBufferSegment a = new GlBufferSegment(null, 0, 1);
        GlBufferSegment b = new GlBufferSegment(null, 1, 1);
        GlBufferSegment c = new GlBufferSegment(null, 2, 1);
        c.setPrev(b);
        assertLinks(c, b, null);
        assertLinks(b, null, null);
        c.setPrev(b);
        assertLinks(c, b, null);
        c.setPrev(a);
        assertLinks(c, a, null);
        assertLinks(a, null, null);
        c.setPrev(null);
        assertLinks(c, null, null);
    }

    // Verifies that mergeInto absorbs the following segment and links the one after it back
    @Test
    void mergeIntoAbsorbsTheFollowingSegment() {
        GlBufferSegment head = new GlBufferSegment(null, 0, 2);
        GlBufferSegment a = new GlBufferSegment(null, 2, 3);
        GlBufferSegment b = new GlBufferSegment(null, 5, 4);
        GlBufferSegment c = new GlBufferSegment(null, 9, 1);
        link(head, a, b, c);
        a.setFree(true);
        b.setFree(true);
        a.mergeInto(b);
        assertRange(a, 2, 7);
        assertLinks(a, head, c);
        assertLinks(c, a, null);
        assertSame(a, head.getNext());
        assertTrue(a.isFree());
        assertRange(b, 5, 4);
        assertLinks(b, a, c);
        assertTrue(b.isFree());
    }

    // Verifies that mergeInto of the last segment ends the list
    @Test
    void mergeIntoTheLastSegmentEndsTheList() {
        GlBufferSegment a = new GlBufferSegment(null, 0, 2);
        GlBufferSegment b = new GlBufferSegment(null, 2, 3);
        link(a, b);
        a.mergeInto(b);
        assertRange(a, 0, 5);
        assertLinks(a, null, null);
        assertFalse(a.isFree());
    }

    // Verifies that mergeInto absorbs a zero length segment and that an empty segment absorbs a longer one
    @Test
    void mergeIntoHandlesZeroLengthSegments() {
        GlBufferSegment a = new GlBufferSegment(null, 0, 2);
        GlBufferSegment empty = new GlBufferSegment(null, 2, 0);
        link(a, empty);
        a.mergeInto(empty);
        assertRange(a, 0, 2);
        assertLinks(a, null, null);
        GlBufferSegment start = new GlBufferSegment(null, 4, 0);
        GlBufferSegment b = new GlBufferSegment(null, 4, 6);
        link(start, b);
        start.mergeInto(b);
        assertRange(start, 4, 6);
        assertLinks(start, null, null);
    }

    // Verifies that mergeInto rejects two zero length segments and leaves both unchanged
    @Test
    void mergeIntoRejectsAnEmptyResult() {
        GlBufferSegment a = new GlBufferSegment(null, 3, 0);
        GlBufferSegment b = new GlBufferSegment(null, 3, 0);
        GlBufferSegment c = new GlBufferSegment(null, 3, 2);
        link(a, b, c);
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> a.mergeInto(b));
        assertEquals(LENGTH_BELOW_ONE, thrown.getMessage());
        assertRange(a, 3, 0);
        assertLinks(a, null, b);
        assertLinks(c, b, null);
    }

    // Verifies that mergeInto rejects null with and without a following segment and leaves the list unchanged
    @Test
    void mergeIntoRejectsNull() {
        GlBufferSegment a = new GlBufferSegment(null, 0, 2);
        GlBufferSegment b = new GlBufferSegment(null, 2, 3);
        link(a, b);
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> a.mergeInto(null));
        assertEquals("Segment must not be null", thrown.getMessage());
        thrown = assertThrows(NullPointerException.class, () -> b.mergeInto(null));
        assertEquals("Segment must not be null", thrown.getMessage());
        assertRange(a, 0, 2);
        assertRange(b, 2, 3);
        assertLinks(a, null, b);
        assertLinks(b, a, null);
    }

    // Verifies that mergeInto rejects every segment other than the following one and leaves the list unchanged
    @Test
    void mergeIntoRejectsASegmentThatDoesNotFollow() {
        GlBufferSegment a = new GlBufferSegment(null, 0, 2);
        GlBufferSegment b = new GlBufferSegment(null, 2, 3);
        GlBufferSegment c = new GlBufferSegment(null, 5, 4);
        GlBufferSegment d = new GlBufferSegment(null, 9, 1);
        GlBufferSegment stray = new GlBufferSegment(null, 2, 3);
        link(a, b, c, d);
        List<Executable> merges = List.of(
                () -> a.mergeInto(stray),
                () -> a.mergeInto(c),
                () -> a.mergeInto(d),
                () -> b.mergeInto(a),
                () -> a.mergeInto(a),
                () -> d.mergeInto(stray),
                () -> d.mergeInto(d));
        for (Executable merge : merges) {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, merge);
            assertEquals("Segment does not follow this segment", thrown.getMessage());
        }
        assertRange(a, 0, 2);
        assertRange(b, 2, 3);
        assertRange(d, 9, 1);
        assertLinks(a, null, b);
        assertLinks(b, a, c);
        assertLinks(c, b, d);
        assertLinks(d, c, null);
        assertLinks(stray, null, null);
    }

    // Verifies that mergeInto rejects a combined length that ends beyond the int range and leaves the list unchanged
    @ParameterizedTest
    @CsvSource({"0, 1073741824, 1073741824", "5, 2147483637, 6", "0, 2147483647, 1", "1, 2147483646, 1", "0, 2147483647, 2147483647", "0, 1, 2147483647"})
    void mergeIntoRejectsAnEndBeyondTheIntRange(int offset, int length, int followingLength) {
        GlBufferSegment a = new GlBufferSegment(null, offset, length);
        GlBufferSegment b = new GlBufferSegment(null, 0, followingLength);
        GlBufferSegment c = new GlBufferSegment(null, 0, 1);
        link(a, b, c);
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> a.mergeInto(b));
        assertEquals(END_BEYOND_RANGE, thrown.getMessage());
        assertRange(a, offset, length);
        assertLinks(a, null, b);
        assertLinks(c, b, null);
    }

    // Verifies that mergeInto accepts a combined length that ends exactly at the int limit
    @Test
    void mergeIntoAcceptsAnEndAtTheIntLimit() {
        GlBufferSegment a = new GlBufferSegment(null, 0, Integer.MAX_VALUE - 1);
        GlBufferSegment b = new GlBufferSegment(null, Integer.MAX_VALUE - 1, 1);
        link(a, b);
        a.mergeInto(b);
        assertRange(a, 0, Integer.MAX_VALUE);
        assertLinks(a, null, null);
    }

    // Verifies that repeated merges coalesce a whole chain into its first segment
    @Test
    void repeatedMergesCoalesceAChain() {
        GlBufferSegment[] chain = new GlBufferSegment[5];
        int offset = 0;
        for (int i = 0; i < chain.length; i++) {
            chain[i] = new GlBufferSegment(null, offset, i + 1);
            offset += i + 1;
        }
        link(chain);
        for (int i = 1; i < chain.length; i++) {
            chain[0].mergeInto(chain[0].getNext());
            assertSame(i + 1 < chain.length ? chain[i + 1] : null, chain[0].getNext());
        }
        assertRange(chain[0], 0, 15);
        assertLinks(chain[0], null, null);
    }

    // Verifies that delete hands the segment to its arena on every call and changes nothing itself
    @Test
    void deleteHandsTheSegmentToItsArenaOnEveryCall() {
        GlBufferArena arena = Mockito.mock(GlBufferArena.class);
        GlBufferSegment segment = new GlBufferSegment(arena, 3, 2);
        segment.delete();
        Mockito.verify(arena).free(same(segment));
        segment.delete();
        Mockito.verify(arena, Mockito.times(2)).free(same(segment));
        Mockito.verifyNoMoreInteractions(arena);
        assertFalse(segment.isFree());
        assertRange(segment, 3, 2);
    }

    // Verifies that delete passes a failure of the arena through unchanged
    @Test
    void deletePassesTheArenaFailureThrough() {
        GlBufferArena arena = Mockito.mock(GlBufferArena.class);
        IllegalStateException failure = new IllegalStateException("Already freed");
        Mockito.doThrow(failure).when(arena).free(any());
        GlBufferSegment segment = new GlBufferSegment(arena, 0, 1);
        assertSame(failure, assertThrows(IllegalStateException.class, segment::delete));
    }

    // Verifies that delete rejects a segment that belongs to no arena and leaves it live
    @Test
    void deleteRejectsASegmentWithoutAnArena() {
        GlBufferSegment segment = new GlBufferSegment(null, 0, 1);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, segment::delete);
        assertEquals("Segment belongs to no arena", thrown.getMessage());
        thrown = assertThrows(IllegalStateException.class, segment::delete);
        assertEquals("Segment belongs to no arena", thrown.getMessage());
        assertFalse(segment.isFree());
        assertRange(segment, 0, 1);
    }

    // Verifies that delete returns the elements to a real arena and that a second delete throws
    @Test
    void deleteReturnsTheElementsToTheArena() {
        GlBufferArena arena = arena();
        GlBufferSegment segment = place(arena, 3)[0];
        assertRange(segment, 3, 3);
        assertEquals(3, arena.getDeviceUsedMemoryL());
        segment.delete();
        assertTrue(segment.isFree());
        assertTrue(arena.isEmpty());
        check(arena);
        IllegalStateException thrown = assertThrows(IllegalStateException.class, segment::delete);
        assertEquals("Already freed", thrown.getMessage());
        assertTrue(arena.isEmpty());
        assertEquals(6, arena.getDeviceAllocatedMemoryL());
        check(arena);
    }

    // Verifies that deletes merge the freed space with both neighbours into one segment that a later upload fills exactly
    @Test
    void deleteMergesTheFreedSpaceWithBothNeighbours() {
        GlBufferArena arena = arena();
        GlBufferSegment[] segments = place(arena, 1, 1, 1);
        assertRange(segments[0], 5, 1);
        assertRange(segments[1], 4, 1);
        assertRange(segments[2], 3, 1);
        segments[0].delete();
        check(arena);
        segments[2].delete();
        check(arena);
        segments[1].delete();
        check(arena);
        assertTrue(arena.isEmpty());
        for (GlBufferSegment segment : segments) {
            assertTrue(segment.isFree());
            assertThrows(IllegalStateException.class, segment::delete);
        }
        Object buffer = arena.getBufferObject();
        GlBufferSegment refill = place(arena, 6)[0];
        assertSame(buffer, arena.getBufferObject());
        assertRange(refill, 0, 6);
        assertLinks(refill, null, null);
        check(arena);
    }

    // Verifies that delete after the arena is deleted only marks the segment free and that a second delete throws
    @Test
    void deleteAfterTheArenaIsDeletedOnlyMarksTheSegmentFree() {
        GlBufferArena arena = arena();
        GlBufferSegment segment = place(arena, 2)[0];
        arena.delete(commands);
        segment.delete();
        assertTrue(segment.isFree());
        assertRange(segment, 2, 2);
        assertEquals(0, arena.getDeviceUsedMemoryL());
        assertThrows(IllegalStateException.class, segment::delete);
    }

    // Verifies that delete of a zero length segment leaves the arena unchanged and that a second delete throws
    @Test
    void deleteOfAZeroLengthSegmentLeavesTheArenaUnchanged() {
        GlBufferArena arena = arena();
        GlBufferSegment[] segments = place(arena, 4, 0);
        GlBufferSegment empty = segments[1];
        assertRange(empty, 0, 0);
        assertLinks(empty, null, null);
        empty.delete();
        assertTrue(empty.isFree());
        assertEquals(4, arena.getDeviceUsedMemoryL());
        check(arena);
        assertThrows(IllegalStateException.class, empty::delete);
        assertEquals(4, arena.getDeviceUsedMemoryL());
        assertFalse(segments[0].isFree());
        check(arena);
    }
}
