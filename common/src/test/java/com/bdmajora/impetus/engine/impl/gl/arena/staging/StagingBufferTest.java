package com.bdmajora.impetus.engine.impl.gl.arena.staging;

import com.bdmajora.impetus.engine.impl.gl.array.GlVertexArray;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapFlags;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferMapping;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferStorageFlags;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferUsage;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlImmutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import com.bdmajora.impetus.engine.impl.gl.device.CommandList;
import com.bdmajora.impetus.engine.impl.gl.device.DrawCommandList;
import com.bdmajora.impetus.engine.impl.gl.sync.GlFence;
import com.bdmajora.impetus.engine.impl.gl.tessellation.GlTessellation;
import com.bdmajora.impetus.engine.impl.gl.util.EnumBitField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

// Tests of the staging buffer contract against every implementation over a simulated GPU
class StagingBufferTest {
    // Number of bytes up to which the simulated GPU keeps buffer contents
    private static final long TRACKED_BYTES = 16L * 1024 * 1024;
    // Number of leading destination bytes compared against the reference contents
    private static final int DESTINATION_BYTES = 512;
    // Value of a destination byte that no copy has written
    private static final int UNWRITTEN = -1;
    // Value of a destination byte written from contents the simulated GPU does not keep
    private static final int UNTRACKED = -2;
    // Number of guard bytes on each side of every mapped range
    private static final int GUARD = 16;
    // Value of every guard byte and of every unmapped byte
    private static final byte GUARD_BYTE = (byte) 0xA5;
    // Length of a copy that neither small ring holds
    private static final int LARGE = 100;

    // Native allocations freed after each test
    private final List<ByteBuffer> allocations = new ArrayList<>();
    // Reference contents of the leading bytes of every destination
    private final Map<GlBuffer, int[]> expected = new IdentityHashMap<>();
    // Simulated GPU behind the command list
    private GpuModel gpu;

    // Implementations of the staging buffer under test
    enum Kind {
        // Fallback path that uploads each copy through a scratch buffer
        FALLBACK {
            // Creates a fallback staging buffer
            @Override
            StagingBuffer create(CommandList commandList) {
                return new FallbackStagingBuffer(commandList);
            }
        },
        // Mapped ring of the default size
        MAPPED {
            // Creates a mapped staging buffer with the default ring
            @Override
            StagingBuffer create(CommandList commandList) {
                return new MappedStagingBuffer(commandList);
            }
        },
        // Mapped ring of 64 bytes
        MAPPED_SMALL_RING {
            // Creates a mapped staging buffer with a ring of 64 bytes
            @Override
            StagingBuffer create(CommandList commandList) {
                return new MappedStagingBuffer(commandList, 64);
            }
        },
        // Mapped ring of one byte
        MAPPED_ONE_BYTE_RING {
            // Creates a mapped staging buffer with a ring of one byte
            @Override
            StagingBuffer create(CommandList commandList) {
                return new MappedStagingBuffer(commandList, 1);
            }
        };

        // Creates the staging buffer of this kind through the command list
        abstract StagingBuffer create(CommandList commandList);
    }

    // Creates the simulated GPU
    @BeforeEach
    void createGpu() {
        gpu = new GpuModel();
    }

    // Checks the guard bytes and the unmapped ranges and frees the native memory of the test
    @AfterEach
    void freeNativeMemory() {
        List<String> damage = gpu.release();
        for (ByteBuffer allocation : allocations) {
            MemoryUtil.memFree(allocation);
        }
        allocations.clear();
        if (!damage.isEmpty()) {
            fail("Writes outside live mappings changed " + damage);
        }
    }

    // Creates the staging buffer under test and clears the construction calls from the count
    private StagingBuffer staging(Kind kind) {
        StagingBuffer staging = kind.create(gpu);
        gpu.resetCalls();
        return staging;
    }

    // Creates a destination buffer with unwritten reference contents
    private GlMutableBuffer destination() {
        GlMutableBuffer buffer = new GlMutableBuffer();
        gpu.addDestination(buffer);
        int[] contents = new int[DESTINATION_BYTES];
        Arrays.fill(contents, UNWRITTEN);
        expected.put(buffer, contents);
        return buffer;
    }

    // Allocates native data whose byte at each index is the seed plus the index
    private ByteBuffer data(int bytes, int seed) {
        ByteBuffer buffer = MemoryUtil.memAlloc(bytes);
        allocations.add(buffer);
        for (int i = 0; i < bytes; i++) {
            buffer.put(i, (byte) (seed + i));
        }
        return buffer;
    }

    // Enqueues a copy and records it in the reference contents of the destination
    private void copy(StagingBuffer staging, ByteBuffer data, GlBuffer dst, long writeOffset) {
        int[] bytes = new int[data.remaining()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = data.get(data.position() + i) & 0xFF;
        }
        staging.enqueueCopy(gpu, data, dst, writeOffset);
        System.arraycopy(bytes, 0, expected.get(dst), Math.toIntExact(writeOffset), bytes.length);
    }

    // Asserts that every destination matches its reference contents
    private void assertContents() {
        for (Map.Entry<GlBuffer, int[]> entry : expected.entrySet()) {
            assertArrayEquals(entry.getValue(), gpu.contents(entry.getKey()));
        }
    }

    // Asserts that the destination holds the remaining bytes of the data at the write offset
    private void assertHolds(GlBuffer dst, long writeOffset, ByteBuffer data) {
        for (int i = 0; i < data.remaining(); i++) {
            assertEquals(data.get(data.position() + i) & 0xFF, gpu.read(dst, writeOffset + i), "destination byte " + (writeOffset + i));
        }
    }

    // Asserts that the copies into the destination tile the range exactly once
    private void assertCovers(GlBuffer dst, long offset, long length) {
        List<Write> writes = new ArrayList<>(gpu.writes(dst));
        writes.sort(Comparator.comparingLong(Write::offset));
        long next = offset;
        for (Write write : writes) {
            assertEquals(next, write.offset());
            next += write.length();
        }
        assertEquals(offset + length, next);
    }

    // Asserts that the staging buffer still copies into a fresh destination and then frees everything on delete
    private void assertStillCopies(StagingBuffer staging) {
        GlMutableBuffer dst = destination();
        copy(staging, data(5, 40), dst, 3);
        staging.flush(gpu);
        assertContents();
        staging.delete(gpu);
        gpu.assertAllFreed();
    }

    // Runs the body and returns whether it threw the injected failure
    private static boolean failsWithInjected(Runnable body) {
        try {
            body.run();
            return false;
        } catch (InjectedFailure failure) {
            return true;
        }
    }

    // Checks that the interface is public and open and declares exactly the four abstract staging methods
    @Test
    void declaresTheStagingContract() throws NoSuchMethodException {
        Class<StagingBuffer> type = StagingBuffer.class;
        assertTrue(type.isInterface());
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertFalse(type.isSealed());
        assertEquals(0, type.getInterfaces().length);
        assertEquals(0, type.getDeclaredClasses().length);
        assertEquals(Set.of(), Arrays.stream(type.getDeclaredFields()).filter(field -> !field.isSynthetic()).map(Field::getName).collect(Collectors.toSet()));
        Set<Method> contract = Set.of(
                type.getMethod("enqueueCopy", CommandList.class, ByteBuffer.class, GlBuffer.class, long.class),
                type.getMethod("flush", CommandList.class),
                type.getMethod("delete", CommandList.class),
                type.getMethod("flip"));
        assertEquals(contract, Arrays.stream(type.getDeclaredMethods()).filter(method -> !method.isSynthetic()).collect(Collectors.toSet()));
        for (Method method : contract) {
            assertTrue(Modifier.isPublic(method.getModifiers()), method.getName());
            assertTrue(Modifier.isAbstract(method.getModifiers()), method.getName());
            assertFalse(method.isDefault(), method.getName());
            assertEquals(void.class, method.getReturnType(), method.getName());
            assertEquals(0, method.getExceptionTypes().length, method.getName());
        }
    }

    // Checks that enqueueCopy rejects a null command list without a call
    @ParameterizedTest
    @EnumSource(Kind.class)
    void enqueueCopyRejectsNullCommandList(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        ByteBuffer data = data(4, 1);
        assertThrows(NullPointerException.class, () -> staging.enqueueCopy(null, data, dst, 0L));
        assertEquals(0, gpu.calls());
        assertStillCopies(staging);
    }

    // Checks that enqueueCopy rejects null data without a call
    @ParameterizedTest
    @EnumSource(Kind.class)
    void enqueueCopyRejectsNullData(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        assertThrows(NullPointerException.class, () -> staging.enqueueCopy(gpu, null, dst, 0L));
        assertEquals(0, gpu.calls());
        assertStillCopies(staging);
    }

    // Checks that enqueueCopy rejects a null destination without a call
    @ParameterizedTest
    @EnumSource(Kind.class)
    void enqueueCopyRejectsNullDestination(Kind kind) {
        StagingBuffer staging = staging(kind);
        ByteBuffer data = data(4, 1);
        assertThrows(NullPointerException.class, () -> staging.enqueueCopy(gpu, data, null, 0L));
        assertEquals(0, gpu.calls());
        assertStillCopies(staging);
    }

    // Checks that enqueueCopy rejects heap data of every length without a call
    @ParameterizedTest
    @EnumSource(Kind.class)
    void enqueueCopyRejectsHeapData(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        for (ByteBuffer heap : List.of(ByteBuffer.allocate(8), ByteBuffer.allocate(0), ByteBuffer.wrap(new byte[4]).asReadOnlyBuffer())) {
            assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(gpu, heap, dst, 0L));
        }
        staging.flush(gpu);
        assertEquals(0, gpu.calls());
        assertContents();
        assertStillCopies(staging);
    }

    // Checks that enqueueCopy copies read only direct data
    @ParameterizedTest
    @EnumSource(Kind.class)
    void enqueueCopyCopiesReadOnlyDirectData(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        copy(staging, data(9, 3).asReadOnlyBuffer(), dst, 11L);
        copy(staging, data(LARGE, 8).asReadOnlyBuffer(), dst, 40L);
        staging.flush(gpu);
        assertContents();
        staging.delete(gpu);
        gpu.assertAllFreed();
    }

    // Checks that enqueueCopy rejects every negative write offset for empty and filled data without a call
    @ParameterizedTest
    @EnumSource(Kind.class)
    void enqueueCopyRejectsNegativeWriteOffsets(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        for (long offset : new long[]{-1L, Integer.MIN_VALUE, Long.MIN_VALUE}) {
            for (ByteBuffer data : List.of(data(4, 1), ByteBuffer.allocateDirect(0))) {
                assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(gpu, data, dst, offset));
            }
        }
        staging.flush(gpu);
        assertEquals(0, gpu.calls());
        assertContents();
        assertStillCopies(staging);
    }

    // Checks that enqueueCopy rejects a write end past the long limit without a call
    @ParameterizedTest
    @EnumSource(Kind.class)
    void enqueueCopyRejectsWriteEndsPastTheLongLimit(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        ByteBuffer huge = MemoryUtil.memByteBuffer(MemoryUtil.memAddress(data(1, 0)), Integer.MAX_VALUE);
        ByteBuffer one = data(1, 0);
        ByteBuffer four = data(4, 0);
        assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(gpu, one, dst, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(gpu, four, dst, Long.MAX_VALUE - 3L));
        assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(gpu, huge, dst, Long.MAX_VALUE - Integer.MAX_VALUE + 1L));
        assertThrows(IllegalArgumentException.class, () -> staging.enqueueCopy(gpu, huge, dst, Long.MAX_VALUE));
        staging.flush(gpu);
        assertEquals(0, gpu.calls());
        assertStillCopies(staging);
    }

    // Checks that a copy ending exactly at the long limit lands after every ring lead in
    @ParameterizedTest
    @EnumSource(Kind.class)
    void enqueueCopyAcceptsWriteEndsAtTheLongLimit(Kind kind) {
        for (int lead : new int[]{0, 1, 62}) {
            StagingBuffer staging = staging(kind);
            GlMutableBuffer dst = destination();
            if (lead > 0) {
                copy(staging, data(lead, 7), dst, 0L);
                staging.flush(gpu);
                gpu.signalAll();
                staging.flip();
            }
            ByteBuffer tail = data(4, 90);
            staging.enqueueCopy(gpu, tail, dst, Long.MAX_VALUE - 4L);
            staging.enqueueCopy(gpu, ByteBuffer.allocateDirect(0), dst, Long.MAX_VALUE);
            staging.flush(gpu);
            assertHolds(dst, Long.MAX_VALUE - 4L, tail);
            assertContents();
            staging.delete(gpu);
        }
        gpu.assertAllFreed();
    }

    // Checks that a copy of the int limit in bytes lands without its bytes being read on the CPU
    @ParameterizedTest
    @EnumSource(Kind.class)
    void enqueueCopyCopiesIntLimitLengths(Kind kind) {
        ByteBuffer huge = MemoryUtil.memByteBuffer(MemoryUtil.memAddress(data(1, 0)), Integer.MAX_VALUE);
        for (long offset : new long[]{0L, Long.MAX_VALUE - Integer.MAX_VALUE}) {
            StagingBuffer staging = staging(kind);
            GlMutableBuffer dst = destination();
            staging.enqueueCopy(gpu, huge, dst, offset);
            staging.flush(gpu);
            assertCovers(dst, offset, Integer.MAX_VALUE);
            assertEquals(0, huge.position());
            assertEquals(Integer.MAX_VALUE, huge.limit());
            staging.delete(gpu);
        }
        gpu.assertAllFreed();
    }

    // Checks that empty data at every position and offset makes no call even after a flush and a flip
    @ParameterizedTest
    @EnumSource(Kind.class)
    void enqueueCopySkipsEmptyData(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        ByteBuffer drained = data(8, 1);
        drained.position(8);
        ByteBuffer middle = data(8, 1);
        middle.position(3).limit(3);
        for (ByteBuffer empty : List.of(ByteBuffer.allocateDirect(0), drained, middle)) {
            for (long offset : new long[]{0L, 1L, Long.MAX_VALUE}) {
                staging.enqueueCopy(gpu, empty, dst, offset);
            }
        }
        staging.flush(gpu);
        staging.flip();
        assertEquals(0, gpu.calls());
        assertEquals(0, gpu.writes(dst).size());
        assertEquals(8, drained.position());
        assertEquals(3, middle.position());
        assertEquals(3, middle.limit());
        assertStillCopies(staging);
    }

    // Checks that single byte copies land at the first and boundary and last offsets
    @ParameterizedTest
    @EnumSource(Kind.class)
    void enqueueCopyCopiesOneByte(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        long[] offsets = {0L, 1L, 63L, 64L, 65L, DESTINATION_BYTES - 1L};
        for (int i = 0; i < offsets.length; i++) {
            copy(staging, data(1, 10 + i), dst, offsets[i]);
        }
        staging.flush(gpu);
        assertContents();
        ByteBuffer last = data(1, 200);
        staging.enqueueCopy(gpu, last, dst, Long.MAX_VALUE - 1L);
        staging.flush(gpu);
        assertHolds(dst, Long.MAX_VALUE - 1L, last);
        assertContents();
        staging.delete(gpu);
        gpu.assertAllFreed();
    }

    // Checks that a copy reads from the position up to the limit and leaves the data unchanged
    @ParameterizedTest
    @EnumSource(Kind.class)
    void enqueueCopyCopiesFromThePosition(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        ByteBuffer data = data(16, 20);
        data.position(3).limit(10);
        copy(staging, data, dst, 5L);
        assertEquals(3, data.position());
        assertEquals(10, data.limit());
        staging.flush(gpu);
        assertEquals(3, data.position());
        assertEquals(10, data.limit());
        for (int i = 0; i < 16; i++) {
            assertEquals((byte) (20 + i), MemoryUtil.memGetByte(MemoryUtil.memAddress0(data) + i), "data byte " + i);
        }
        assertContents();
        staging.delete(gpu);
        gpu.assertAllFreed();
    }

    // Checks that copies from misaligned native addresses into odd offsets land
    @ParameterizedTest
    @EnumSource(Kind.class)
    void enqueueCopyReadsMisalignedAddresses(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        ByteBuffer block = data(32, 60);
        for (int shift = 1; shift < 8; shift++) {
            copy(staging, MemoryUtil.memByteBuffer(MemoryUtil.memAddress(block) + shift, 13), dst, 7L + 20L * shift);
        }
        staging.flush(gpu);
        assertContents();
        staging.delete(gpu);
        gpu.assertAllFreed();
    }

    // Checks that a copy keeps the bytes the data held when enqueueCopy returned
    @ParameterizedTest
    @EnumSource(Kind.class)
    void enqueueCopyCapturesTheBytesBeforeReturning(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        for (int length : new int[]{12, LARGE}) {
            ByteBuffer data = data(length, length);
            copy(staging, data, dst, length == LARGE ? 100L : 0L);
            MemoryUtil.memSet(MemoryUtil.memAddress(data), 0xEE, length);
        }
        staging.flush(gpu);
        assertContents();
        staging.delete(gpu);
        gpu.assertAllFreed();
    }

    // Checks that later copies into overlapping ranges overwrite earlier ones within and across flushes
    @ParameterizedTest
    @EnumSource(Kind.class)
    void laterCopiesOverwriteEarlierCopies(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        copy(staging, data(4, 1), dst, 0L);
        copy(staging, data(LARGE, 2), dst, 0L);
        copy(staging, data(4, 3), dst, 2L);
        copy(staging, data(2, 4), dst, 1L);
        copy(staging, data(LARGE, 5), dst, 50L);
        copy(staging, data(3, 6), dst, 60L);
        staging.flush(gpu);
        assertContents();
        copy(staging, data(8, 7), dst, 0L);
        staging.flush(gpu);
        copy(staging, data(8, 8), dst, 4L);
        staging.flush(gpu);
        assertContents();
        staging.delete(gpu);
        gpu.assertAllFreed();
    }

    // Checks that interleaved copies into two destinations land in their own destination
    @ParameterizedTest
    @EnumSource(Kind.class)
    void copiesLandInEveryDestination(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer first = destination();
        GlMutableBuffer second = destination();
        for (int i = 0; i < 12; i++) {
            copy(staging, data(5, i), (i & 1) == 0 ? first : second, 5L * i);
        }
        staging.flush(gpu);
        assertContents();
        staging.delete(gpu);
        gpu.assertAllFreed();
    }

    // Checks that repeated copies of the same data into adjacent ranges land
    @ParameterizedTest
    @EnumSource(Kind.class)
    void adjacentCopiesOfTheSameDataLand(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        ByteBuffer same = data(4, 9);
        for (int i = 0; i < 20; i++) {
            copy(staging, same, dst, 4L * i);
        }
        staging.flush(gpu);
        assertContents();
        staging.delete(gpu);
        gpu.assertAllFreed();
    }

    // Checks that copies of every length near the ring sizes land after every lead in and after a reclaim
    @ParameterizedTest
    @EnumSource(Kind.class)
    void copiesOfEveryLengthNearTheRingEndLand(Kind kind) {
        for (int lead : new int[]{0, 1, 63}) {
            for (int length : new int[]{1, 2, 3, 63, 64, 65, 127, 128, 129}) {
                StagingBuffer staging = staging(kind);
                GlMutableBuffer dst = destination();
                if (lead > 0) {
                    copy(staging, data(lead, lead), dst, 0L);
                }
                copy(staging, data(length, length), dst, 200L);
                staging.flush(gpu);
                assertContents();
                gpu.signalAll();
                staging.flip();
                copy(staging, data(length, length + 1), dst, 300L);
                staging.flush(gpu);
                assertContents();
                staging.delete(gpu);
            }
        }
        gpu.assertAllFreed();
    }

    // Checks that random sequences of copies and flushes and flips leave every destination equal to sequential copies
    @ParameterizedTest
    @EnumSource(Kind.class)
    void randomSequencesMatchSequentialCopies(Kind kind) {
        ByteBuffer source = data(LARGE + 4, 0);
        for (long seed = 1L; seed <= 6L; seed++) {
            SplittableRandom random = new SplittableRandom(seed);
            StagingBuffer staging = staging(kind);
            GlMutableBuffer[] targets = {destination(), destination()};
            for (int step = 0; step < 400; step++) {
                int roll = random.nextInt(100);
                if (roll < 70) {
                    int length = random.nextInt(4) == 0 ? random.nextInt(LARGE) : random.nextInt(17);
                    int position = random.nextInt(4);
                    for (int i = 0; i < position + length; i++) {
                        source.put(i, (byte) random.nextInt(256));
                    }
                    source.limit(position + length).position(position);
                    copy(staging, source, targets[random.nextInt(2)], random.nextInt(DESTINATION_BYTES - length + 1));
                    source.clear();
                } else if (roll < 82) {
                    staging.flush(gpu);
                    assertContents();
                } else if (roll < 92) {
                    gpu.signalOldest(random.nextInt(4));
                    staging.flip();
                } else {
                    staging.flip();
                }
            }
            staging.flush(gpu);
            assertContents();
            staging.delete(gpu);
        }
        gpu.assertAllFreed();
    }

    // Checks that a copy whose call fails keeps every earlier copy for the next flush
    @ParameterizedTest
    @EnumSource(Kind.class)
    void enqueueCopyFailureKeepsEarlierCopies(Kind kind) {
        for (int failing = 0; failing < 6; failing++) {
            StagingBuffer staging = staging(kind);
            GlMutableBuffer dst = destination();
            copy(staging, data(1, failing), dst, 0L);
            ByteBuffer large = data(LARGE, 50 + failing);
            gpu.failCall(failing);
            boolean failed = failsWithInjected(() -> copy(staging, large, dst, 10L));
            gpu.disarm();
            staging.flush(gpu);
            assertContents();
            assertEquals(!failed, gpu.read(dst, 10L) == ((50 + failing) & 0xFF), "copy landed after " + (failed ? "a failure" : "success"));
            staging.delete(gpu);
        }
        gpu.assertAllFreed();
    }

    // Checks that flush rejects a null command list without a call and keeps the pending copies
    @ParameterizedTest
    @EnumSource(Kind.class)
    void flushRejectsNullCommandList(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        copy(staging, data(6, 2), dst, 4L);
        copy(staging, data(LARGE, 3), dst, 20L);
        int before = gpu.calls();
        assertThrows(NullPointerException.class, () -> staging.flush(null));
        assertEquals(before, gpu.calls());
        staging.flush(gpu);
        assertContents();
        staging.delete(gpu);
        gpu.assertAllFreed();
    }

    // Checks that flush and flip on a staging buffer without copies make no call
    @ParameterizedTest
    @EnumSource(Kind.class)
    void flushWithoutCopiesMakesNoCall(Kind kind) {
        StagingBuffer staging = staging(kind);
        for (int i = 0; i < 3; i++) {
            staging.flush(gpu);
            staging.flip();
        }
        assertEquals(0, gpu.calls());
        staging.delete(gpu);
        gpu.assertAllFreed();
    }

    // Checks that a flush after a flush makes no call
    @ParameterizedTest
    @EnumSource(Kind.class)
    void repeatedFlushMakesNoCall(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        copy(staging, data(6, 2), dst, 4L);
        copy(staging, data(LARGE, 3), dst, 20L);
        staging.flush(gpu);
        gpu.resetCalls();
        staging.flush(gpu);
        staging.flush(gpu);
        assertEquals(0, gpu.calls());
        assertContents();
        staging.delete(gpu);
        gpu.assertAllFreed();
    }

    // Checks that a flush whose call fails is completed by the next flush
    @ParameterizedTest
    @EnumSource(Kind.class)
    void flushRetriesAfterAFailedCall(Kind kind) {
        for (int failing = 0; failing < 6; failing++) {
            StagingBuffer staging = staging(kind);
            GlMutableBuffer dst = destination();
            copy(staging, data(LARGE, failing), dst, 0L);
            copy(staging, data(1, 7), dst, 200L);
            copy(staging, data(1, 9), dst, 201L);
            gpu.failCall(failing);
            failsWithInjected(() -> staging.flush(gpu));
            gpu.disarm();
            staging.flush(gpu);
            assertContents();
            staging.delete(gpu);
        }
        gpu.assertAllFreed();
    }

    // Checks that delete rejects a null command list without a call and keeps the staging buffer usable
    @ParameterizedTest
    @EnumSource(Kind.class)
    void deleteRejectsNullCommandList(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        copy(staging, data(6, 2), dst, 4L);
        staging.flush(gpu);
        int before = gpu.calls();
        assertThrows(NullPointerException.class, () -> staging.delete(null));
        assertEquals(before, gpu.calls());
        assertContents();
        assertStillCopies(staging);
    }

    // Checks that delete frees every resource when fresh and with pending copies and with unsignalled fences
    @ParameterizedTest
    @EnumSource(Kind.class)
    void deleteFreesEveryResource(Kind kind) {
        staging(kind).delete(gpu);
        gpu.assertAllFreed();
        GlMutableBuffer dst = destination();
        StagingBuffer pending = staging(kind);
        pending.enqueueCopy(gpu, data(3, 1), dst, 0L);
        pending.enqueueCopy(gpu, data(LARGE, 1), dst, 10L);
        pending.delete(gpu);
        gpu.assertAllFreed();
        StagingBuffer fenced = staging(kind);
        for (int i = 0; i < 5; i++) {
            fenced.enqueueCopy(gpu, data(1, i), dst, i);
            fenced.flush(gpu);
        }
        gpu.signalOldest(2);
        fenced.flip();
        fenced.delete(gpu);
        gpu.assertAllFreed();
    }

    // Checks that delete issues no copy of pending data
    @ParameterizedTest
    @EnumSource(Kind.class)
    void deleteIssuesNoCopy(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        staging.enqueueCopy(gpu, data(3, 1), dst, 0L);
        int writes = gpu.writes(dst).size();
        staging.delete(gpu);
        assertEquals(writes, gpu.writes(dst).size());
        gpu.assertAllFreed();
    }

    // Checks that a delete after a delete makes no call
    @ParameterizedTest
    @EnumSource(Kind.class)
    void repeatedDeleteMakesNoCall(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        copy(staging, data(6, 2), dst, 4L);
        staging.flush(gpu);
        staging.delete(gpu);
        gpu.resetCalls();
        staging.delete(gpu);
        staging.delete(gpu);
        assertEquals(0, gpu.calls());
        gpu.assertAllFreed();
    }

    // Checks that every method after delete rejects use or ignores the call without touching freed memory
    @ParameterizedTest
    @EnumSource(Kind.class)
    void useAfterDeleteIsRejected(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        copy(staging, data(4, 1), dst, 0L);
        staging.flush(gpu);
        staging.delete(gpu);
        gpu.resetCalls();
        ByteBuffer data = data(4, 2);
        ByteBuffer large = data(LARGE, 3);
        assertThrows(IllegalStateException.class, () -> staging.enqueueCopy(gpu, data, dst, 8L));
        assertThrows(IllegalStateException.class, () -> staging.enqueueCopy(gpu, ByteBuffer.allocateDirect(0), dst, 8L));
        assertThrows(IllegalStateException.class, () -> staging.enqueueCopy(gpu, large, dst, 8L));
        assertThrows(IllegalStateException.class, () -> staging.flush(gpu));
        staging.flip();
        staging.delete(gpu);
        assertThrows(NullPointerException.class, () -> staging.enqueueCopy(null, data, dst, 8L));
        assertThrows(NullPointerException.class, () -> staging.flush(null));
        assertThrows(NullPointerException.class, () -> staging.delete(null));
        assertEquals(0, gpu.calls());
        assertContents();
        gpu.assertAllFreed();
    }

    // Checks that flip before any copy makes no call
    @ParameterizedTest
    @EnumSource(Kind.class)
    void flipBeforeUseMakesNoCall(Kind kind) {
        StagingBuffer staging = staging(kind);
        staging.flip();
        staging.flip();
        assertEquals(0, gpu.calls());
        staging.delete(gpu);
        gpu.assertAllFreed();
    }

    // Checks that flip issues no pending copy and makes no call without fences
    @ParameterizedTest
    @EnumSource(Kind.class)
    void flipIssuesNoCopy(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        copy(staging, data(1, 5), dst, 9L);
        int writes = gpu.writes(dst).size();
        gpu.resetCalls();
        staging.flip();
        staging.flip();
        assertEquals(0, gpu.calls());
        assertEquals(writes, gpu.writes(dst).size());
        staging.flush(gpu);
        assertContents();
        staging.delete(gpu);
        gpu.assertAllFreed();
    }

    // Checks that flip never deletes a fence the GPU has not signalled
    @ParameterizedTest
    @EnumSource(Kind.class)
    void flipDeletesOnlySignalledFences(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        for (int i = 0; i < 4; i++) {
            copy(staging, data(1, i), dst, i);
            staging.flush(gpu);
        }
        gpu.signal(1);
        gpu.signal(3);
        staging.flip();
        gpu.assertNoFenceDeletedEarly();
        gpu.signalAll();
        staging.flip();
        assertEquals(0, gpu.liveFences());
        gpu.assertNoFenceDeletedEarly();
        assertContents();
        staging.delete(gpu);
        gpu.assertAllFreed();
    }

    // Checks that flip releases every signalled fence on every cycle
    @ParameterizedTest
    @EnumSource(Kind.class)
    void flipReleasesSignalledFencesEveryCycle(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        ByteBuffer data = data(1, 0);
        for (int cycle = 0; cycle < 100; cycle++) {
            data.put(0, (byte) cycle);
            copy(staging, data, dst, cycle);
            staging.flush(gpu);
            gpu.signalAll();
            staging.flip();
            assertEquals(0, gpu.liveFences(), "cycle " + cycle);
        }
        assertContents();
        staging.delete(gpu);
        gpu.assertAllFreed();
    }

    // Checks that copies keep landing when the GPU never signals a fence
    @ParameterizedTest
    @EnumSource(Kind.class)
    void copiesLandWhenTheGpuNeverSignals(Kind kind) {
        StagingBuffer staging = staging(kind);
        GlMutableBuffer dst = destination();
        for (int i = 0; i < 150; i++) {
            copy(staging, data(3, i), dst, 3L * i);
            staging.flush(gpu);
            staging.flip();
        }
        assertContents();
        staging.delete(gpu);
        gpu.assertAllFreed();
    }

    // Failure the simulated GPU throws from an armed call
    private static final class InjectedFailure extends RuntimeException {
        // Creates the failure without a stack trace
        private InjectedFailure() {
            super("Injected failure", null, false, false);
        }
    }

    // Roles of the buffers the simulated GPU knows
    private enum Role {
        // Mutable buffer a staging buffer created
        SCRATCH,
        // Immutable buffer a staging buffer created
        RING,
        // Destination buffer a test created
        DESTINATION
    }

    // Copy of a byte range into a destination with its contents and issue stamp
    private record Write(long offset, long length, byte[] content, long stamp) {
    }

    // Simulated storage of one buffer
    private static final class Storage {
        // Role of the buffer
        private final Role role;
        // Value of each leading destination byte
        private final int[] values;
        // Issue stamp of each leading destination byte
        private final long[] stamps;
        // Copies into the destination in issue order
        private final List<Write> writes = new ArrayList<>();
        // Copies into the destination that reach past the leading bytes or carry no contents
        private final List<Write> farWrites = new ArrayList<>();
        // Size of the storage in bytes
        private long size;
        // Contents visible to copies or null past the tracked size
        private byte[] bytes;
        // Live mapping of the buffer or null
        private MappedRange mapping;
        // Flag set once the buffer is deleted
        private boolean deleted;

        // Creates zeroed storage of the role and size
        private Storage(Role role, long size) {
            this.role = role;
            this.size = size;
            this.bytes = size <= TRACKED_BYTES ? new byte[(int) size] : null;
            this.values = role == Role.DESTINATION ? new int[DESTINATION_BYTES] : null;
            this.stamps = role == Role.DESTINATION ? new long[DESTINATION_BYTES] : null;
            if (this.values != null) {
                Arrays.fill(this.values, UNWRITTEN);
            }
        }

        // Records a copy into the destination
        private void record(Write write) {
            this.writes.add(write);
            if (write.content() == null || write.offset() + write.length() > DESTINATION_BYTES) {
                this.farWrites.add(write);
                return;
            }
            for (int i = 0; i < write.length(); i++) {
                this.values[(int) write.offset() + i] = write.content()[i] & 0xFF;
                this.stamps[(int) write.offset() + i] = write.stamp();
            }
        }

        // Returns the value of the destination byte at the offset from the latest copy that wrote it
        private int read(long at) {
            int value = UNWRITTEN;
            long stamp = 0L;
            if (at < DESTINATION_BYTES) {
                value = this.values[(int) at];
                stamp = this.stamps[(int) at];
            }
            for (Write write : this.farWrites) {
                long index = at - write.offset();
                if (write.stamp() > stamp && index >= 0L && index < write.length()) {
                    stamp = write.stamp();
                    value = write.content() == null ? UNTRACKED : write.content()[(int) index] & 0xFF;
                }
            }
            return value;
        }
    }

    // Mapped range of a ring and the guarded native block behind it
    private static final class MappedRange {
        // Storage of the mapped buffer
        private final Storage storage;
        // Offset of the range in the buffer
        private final long offset;
        // Native block with guard bytes on both sides of the range
        private final ByteBuffer block;
        // View of the mapped bytes inside the block
        private final ByteBuffer view;
        // Flag set once the range is unmapped
        private boolean unmapped;
        // Flag set once the native block is freed
        private boolean freed;

        // Creates the range over the block
        private MappedRange(Storage storage, long offset, ByteBuffer block, ByteBuffer view) {
            this.storage = storage;
            this.offset = offset;
            this.block = block;
            this.view = view;
        }
    }

    // Command list over a simulated GPU that rejects every call breaking the GL rules
    private static final class GpuModel implements CommandList {
        // Storage of every buffer the model knows
        private final Map<GlBuffer, Storage> storages = new IdentityHashMap<>();
        // Range of every mapping the model handed out
        private final Map<GlBufferMapping, MappedRange> mappings = new IdentityHashMap<>();
        // Fences the model handed out in creation order
        private final List<ModelFence> fences = new ArrayList<>();
        // Number of calls since the last reset
        private int calls;
        // Index of the call that throws the injected failure and negative when none is armed
        private int failAt = -1;
        // Issue stamp of the latest copy
        private long stamp;

        // Counts a call and throws the injected failure when the call is the armed one
        private void count() {
            int index = this.calls++;
            if (index == this.failAt) {
                this.failAt = -1;
                throw new InjectedFailure();
            }
        }

        // Returns the number of calls since the last reset
        private int calls() {
            return this.calls;
        }

        // Clears the call count and disarms the injected failure
        private void resetCalls() {
            this.calls = 0;
            this.failAt = -1;
        }

        // Arms the injected failure for the call at the index counted from now
        private void failCall(int index) {
            this.failAt = this.calls + index;
        }

        // Disarms the injected failure
        private void disarm() {
            this.failAt = -1;
        }

        // Registers a buffer as a copy destination
        private void addDestination(GlBuffer buffer) {
            this.storages.put(buffer, new Storage(Role.DESTINATION, 0L));
        }

        // Returns the live storage of the buffer and rejects unknown or deleted buffers
        private Storage live(GlBuffer buffer, Role role) {
            Storage storage = this.storages.get(buffer);
            if (storage == null) {
                throw new AssertionError("Uses a buffer the GPU never created");
            }
            if (storage.deleted) {
                throw new AssertionError("Uses a deleted " + storage.role + " buffer");
            }
            if (role != null && storage.role != role) {
                throw new AssertionError("Uses a " + storage.role + " buffer as a " + role + " buffer");
            }
            return storage;
        }

        // Returns the live range of the mapping and rejects unknown or unmapped mappings
        private MappedRange live(GlBufferMapping mapping) {
            MappedRange range = this.mappings.get(mapping);
            if (range == null) {
                throw new AssertionError("Uses a mapping the GPU never handed out");
            }
            if (range.unmapped) {
                throw new AssertionError("Uses an unmapped mapping");
            }
            return range;
        }

        // Creates a scratch buffer without storage
        @Override
        public GlMutableBuffer createMutableBuffer() {
            this.count();
            GlMutableBuffer buffer = new GlMutableBuffer();
            this.storages.put(buffer, new Storage(Role.SCRATCH, 0L));
            return buffer;
        }

        // Creates a ring buffer of zeroed storage and rejects a size without bytes
        @Override
        public GlImmutableBuffer createImmutableBuffer(long bufferSize, EnumBitField<GlBufferStorageFlags> flags) {
            this.count();
            if (bufferSize <= 0L) {
                throw new AssertionError("Creates a buffer of " + bufferSize + " bytes");
            }
            GlImmutableBuffer buffer = new GlImmutableBuffer(flags);
            this.storages.put(buffer, new Storage(Role.RING, bufferSize));
            return buffer;
        }

        // Replaces the scratch storage with the remaining bytes of the direct data
        @Override
        public void uploadData(GlMutableBuffer glBuffer, ByteBuffer byteBuffer, GlBufferUsage usage) {
            this.count();
            Storage storage = this.live(glBuffer, Role.SCRATCH);
            if (!byteBuffer.isDirect()) {
                throw new AssertionError("Uploads a heap buffer");
            }
            int length = byteBuffer.remaining();
            storage.size = length;
            storage.bytes = null;
            if (length <= TRACKED_BYTES) {
                storage.bytes = new byte[length];
                byteBuffer.duplicate().get(storage.bytes);
            }
        }

        // Records a copy from a staging buffer into a destination and rejects ranges outside either buffer
        @Override
        public void copyBufferSubData(GlBuffer src, GlBuffer dst, long readOffset, long writeOffset, long bytes) {
            this.count();
            Storage from = this.live(src, null);
            Storage to = this.live(dst, Role.DESTINATION);
            if (from.role == Role.DESTINATION) {
                throw new AssertionError("Copies from a destination buffer");
            }
            if (bytes <= 0L || readOffset < 0L || writeOffset < 0L || readOffset > from.size - bytes || writeOffset > Long.MAX_VALUE - bytes) {
                throw new AssertionError("Copies " + bytes + " bytes from " + readOffset + " of " + from.size + " into " + writeOffset);
            }
            byte[] content = from.bytes == null ? null : Arrays.copyOfRange(from.bytes, (int) readOffset, (int) (readOffset + bytes));
            to.record(new Write(writeOffset, bytes, content, ++this.stamp));
        }

        // Replaces the scratch storage with zeroed bytes of the size and rejects a negative size
        @Override
        public void allocateStorage(GlMutableBuffer buffer, long bufferSize, GlBufferUsage usage) {
            this.count();
            Storage storage = this.live(buffer, Role.SCRATCH);
            if (bufferSize < 0L) {
                throw new AssertionError("Allocates " + bufferSize + " bytes");
            }
            storage.size = bufferSize;
            storage.bytes = bufferSize <= TRACKED_BYTES ? new byte[(int) bufferSize] : null;
        }

        // Unmaps and deletes a staging buffer and checks and frees the native blocks of its mappings
        @Override
        public void deleteBuffer(GlBuffer buffer) {
            this.count();
            Storage storage = this.live(buffer, null);
            if (storage.role == Role.DESTINATION) {
                throw new AssertionError("Deletes a destination buffer");
            }
            if (storage.mapping != null) {
                unmapRange(storage.mapping);
            }
            storage.deleted = true;
            storage.bytes = null;
            for (MappedRange range : this.mappings.values()) {
                if (range.storage == storage && !range.freed) {
                    String damage = damage(range);
                    free(range);
                    if (damage != null) {
                        throw new AssertionError("Writes outside the mapping changed " + damage);
                    }
                }
            }
        }

        // Maps the range of a ring into a guarded native block and rejects ranges outside the ring
        @Override
        public GlBufferMapping mapBuffer(GlBuffer buffer, long offset, long length, EnumBitField<GlBufferMapFlags> flags) {
            this.count();
            Storage storage = this.live(buffer, Role.RING);
            if (storage.mapping != null) {
                throw new AssertionError("Maps a mapped buffer");
            }
            if (offset < 0L || length <= 0L || offset > storage.size - length || length > Integer.MAX_VALUE - 2 * GUARD) {
                throw new AssertionError("Maps " + length + " bytes at " + offset + " of " + storage.size);
            }
            ByteBuffer block = MemoryUtil.memAlloc((int) length + 2 * GUARD);
            MemoryUtil.memSet(MemoryUtil.memAddress(block), GUARD_BYTE, block.capacity());
            MemoryUtil.memSet(MemoryUtil.memAddress(block) + GUARD, 0, length);
            ByteBuffer view = MemoryUtil.memByteBuffer(MemoryUtil.memAddress(block) + GUARD, (int) length);
            GlBufferMapping mapping = new GlBufferMapping(buffer, view);
            MappedRange range = new MappedRange(storage, offset, block, view);
            storage.mapping = range;
            this.mappings.put(mapping, range);
            return mapping;
        }

        // Unmaps a live mapping
        @Override
        public void unmap(GlBufferMapping map) {
            this.count();
            unmapRange(this.live(map));
        }

        // Makes the flushed mapped bytes visible to copies and rejects ranges outside the mapping
        @Override
        public void flushMappedRange(GlBufferMapping map, int offset, int length) {
            this.count();
            MappedRange range = this.live(map);
            if (offset < 0 || length < 0 || offset > range.view.capacity() - length) {
                throw new AssertionError("Flushes " + length + " bytes at " + offset + " of " + range.view.capacity());
            }
            if (range.storage.bytes != null) {
                range.view.get(offset, range.storage.bytes, (int) range.offset + offset, length);
            }
        }

        // Creates an unsignalled fence
        @Override
        public GlFence createFence() {
            this.count();
            ModelFence fence = new ModelFence();
            this.fences.add(fence);
            return fence;
        }

        // Rejects a vertex array bind
        @Override
        public void bindVertexArray(GlVertexArray array) {
            throw unexpected("bindVertexArray");
        }

        // Rejects an upload from a raw pointer
        @Override
        public void uploadData(GlMutableBuffer glBuffer, long ptr, long bytes, GlBufferUsage usage) {
            throw unexpected("uploadData from a pointer");
        }

        // Rejects a buffer bind
        @Override
        public void bindBuffer(GlBufferTarget target, GlBuffer buffer) {
            throw unexpected("bindBuffer");
        }

        // Rejects a vertex array unbind
        @Override
        public void unbindVertexArray() {
            throw unexpected("unbindVertexArray");
        }

        // Rejects a vertex array delete
        @Override
        public void deleteVertexArray(GlVertexArray vertexArray) {
            throw unexpected("deleteVertexArray");
        }

        // Rejects a command list flush
        @Override
        public void flush() {
            throw unexpected("flush");
        }

        // Rejects the start of a tessellation
        @Override
        public DrawCommandList beginTessellating(GlTessellation tessellation) {
            throw unexpected("beginTessellating");
        }

        // Rejects a tessellation delete
        @Override
        public void deleteTessellation(GlTessellation tessellation) {
            throw unexpected("deleteTessellation");
        }

        // Returns the failure of a call no staging buffer makes
        private static AssertionError unexpected(String call) {
            return new AssertionError("Unexpected call of " + call);
        }

        // Marks the range unmapped and fills its bytes with the guard value
        private static void unmapRange(MappedRange range) {
            range.unmapped = true;
            range.storage.mapping = null;
            MemoryUtil.memSet(MemoryUtil.memAddress(range.view), GUARD_BYTE, range.view.capacity());
        }

        // Returns the damage in the native block of the range or null when it is intact
        private static String damage(MappedRange range) {
            ByteBuffer block = range.block;
            int end = block.capacity();
            for (int i = 0; i < GUARD; i++) {
                if (block.get(i) != GUARD_BYTE || block.get(end - 1 - i) != GUARD_BYTE) {
                    return "guard byte " + i + " of a range of " + (end - 2 * GUARD) + " bytes";
                }
            }
            if (range.unmapped) {
                for (int i = GUARD; i < end - GUARD; i++) {
                    if (block.get(i) != GUARD_BYTE) {
                        return "byte " + (i - GUARD) + " of an unmapped range";
                    }
                }
            }
            return null;
        }

        // Frees the native block of the range
        private static void free(MappedRange range) {
            range.freed = true;
            MemoryUtil.memFree(range.block);
        }

        // Checks and frees every native block still held and returns the damage found
        private List<String> release() {
            List<String> found = new ArrayList<>();
            for (MappedRange range : this.mappings.values()) {
                if (!range.freed) {
                    String damage = damage(range);
                    if (damage != null) {
                        found.add(damage);
                    }
                    free(range);
                }
            }
            return found;
        }

        // Returns the leading bytes of the destination
        private int[] contents(GlBuffer buffer) {
            Storage storage = this.storages.get(buffer);
            int[] contents = new int[DESTINATION_BYTES];
            for (int i = 0; i < DESTINATION_BYTES; i++) {
                contents[i] = storage.read(i);
            }
            return contents;
        }

        // Returns the value of the destination byte at the offset
        private int read(GlBuffer buffer, long at) {
            return this.storages.get(buffer).read(at);
        }

        // Returns the copies into the destination in issue order
        private List<Write> writes(GlBuffer buffer) {
            return this.storages.get(buffer).writes;
        }

        // Signals the fence at the creation index when it exists
        private void signal(int index) {
            if (index < this.fences.size()) {
                this.fences.get(index).signalled = true;
            }
        }

        // Signals the oldest unsignalled fences up to the count
        private void signalOldest(int count) {
            for (ModelFence fence : this.fences) {
                if (count == 0) {
                    return;
                }
                if (!fence.signalled) {
                    fence.signalled = true;
                    count--;
                }
            }
        }

        // Signals every fence
        private void signalAll() {
            for (ModelFence fence : this.fences) {
                fence.signalled = true;
            }
        }

        // Returns the number of fences not yet deleted
        private int liveFences() {
            int live = 0;
            for (ModelFence fence : this.fences) {
                if (!fence.deleted) {
                    live++;
                }
            }
            return live;
        }

        // Asserts that no fence was deleted before it signalled
        private void assertNoFenceDeletedEarly() {
            for (int i = 0; i < this.fences.size(); i++) {
                assertFalse(this.fences.get(i).deletedEarly, "fence " + i + " deleted before it signalled");
            }
        }

        // Asserts that every buffer and mapping and fence the staging buffers created is freed
        private void assertAllFreed() {
            for (Storage storage : this.storages.values()) {
                assertTrue(storage.role == Role.DESTINATION || storage.deleted, "live " + storage.role + " buffer");
            }
            for (MappedRange range : this.mappings.values()) {
                assertTrue(range.unmapped, "live mapping");
            }
            for (int i = 0; i < this.fences.size(); i++) {
                assertTrue(this.fences.get(i).deleted, "live fence " + i);
            }
        }

        // Fence whose signal state the test controls
        private final class ModelFence extends GlFence {
            // Flag set once the test signals the fence
            private boolean signalled;
            // Flag set once the fence is deleted
            private boolean deleted;
            // Flag set when the fence was deleted before it signalled
            private boolean deletedEarly;

            // Creates a fence without a GL sync object
            private ModelFence() {
                super(0L);
            }

            // Returns whether the test signalled the fence and rejects a query after delete
            @Override
            public boolean isCompleted() {
                GpuModel.this.count();
                if (this.deleted) {
                    throw new AssertionError("Queries a deleted fence");
                }
                return this.signalled;
            }

            // Marks the fence deleted and rejects a second delete
            @Override
            public void delete() {
                GpuModel.this.count();
                if (this.deleted) {
                    throw new AssertionError("Deletes a fence twice");
                }
                this.deleted = true;
                this.deletedEarly = !this.signalled;
            }
        }
    }
}
