package com.bdmajora.impetus.lwjgl.lwjgl2.memory;

import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

import static com.bdmajora.impetus.lwjgl.lwjgl2.memory.MemoryStack.*;
import static org.junit.jupiter.api.Assertions.*;

class MemoryStackTest {
    private static Pointer at(long address) {
        return () -> address;
    }

    @Test
    void stacksComeFromBuffersOrRawAddresses() {
        MemoryStack whole = MemoryStack.create();
        assertEquals(DEFAULT_STACK_SIZE, whole.getSize());
        assertEquals(DEFAULT_STACK_SIZE, whole.getPointer());
        assertEquals(0, whole.getFrameIndex());
        assertEquals(whole.getAddress() + DEFAULT_STACK_SIZE, whole.getPointerAddress());
        assertEquals(256, MemoryStack.create(256).getSize());

        ByteBuffer backing = BufferUtils.createByteBuffer(128);
        backing.position(32);
        MemoryStack sliced = MemoryStack.create(backing);
        assertEquals(96, sliced.getSize());
        assertEquals(MemoryUtilities.memAddress(backing), sliced.getAddress());
        assertEquals(sliced.getAddress(), sliced.address());

        long raw = MemoryUtilities.nmemAlloc(64);
        MemoryStack bare = MemoryStack.ncreate(raw, 64);
        assertEquals(raw, bare.getAddress());
        assertEquals(64, bare.getSize());
        MemoryUtilities.nmemFree(raw);
    }

    @Test
    void framesRestoreThePointerAndGrowPastEightDeep() {
        MemoryStack stack = MemoryStack.create(1024);
        try (MemoryStack frame = stack.push()) {
            assertSame(stack, frame);
            assertEquals(1, stack.getFrameIndex());
            long a = stack.nmalloc(16);
            assertEquals(0, a & 7);
            assertEquals(1024 - 16, stack.getPointer());
            long b = stack.nmalloc(32, 1);
            assertEquals(0, b & 31);
            assertTrue(b < a);
            long c = stack.ncalloc(4, 3, 4);
            assertEquals(0, c & 3);
            for (int i = 0; i < 12; i++) {
                assertEquals(0, MemoryUtilities.memGetByte(c + i));
            }
            assertThrows(OutOfMemoryError.class, () -> stack.nmalloc(8, 2048));
        }
        assertEquals(0, stack.getFrameIndex());
        assertEquals(1024, stack.getPointer());
        assertSame(stack, stack.push().pop());

        stack.setPointer(512);
        assertEquals(512, stack.getPointer());
        assertEquals(stack.getAddress() + 512, stack.getPointerAddress());
        assertThrows(IndexOutOfBoundsException.class, () -> stack.setPointer(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> stack.setPointer(1025));
        stack.setPointer(1024);

        // The ninth push outgrows the initial frame array with a warning on stderr
        PrintStream err = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured));
        try {
            for (int i = 0; i < DEFAULT_STACK_FRAMES + 1; i++) {
                stack.push();
                stack.nmalloc(8);
            }
        } finally {
            System.setErr(err);
        }
        assertTrue(captured.toString().contains("Out of frame stack space"));
        assertEquals(DEFAULT_STACK_FRAMES + 1, stack.getFrameIndex());
        for (int i = 0; i < DEFAULT_STACK_FRAMES + 1; i++) {
            stack.pop();
        }
        assertEquals(1024, stack.getPointer());
    }

    @Test
    void typedAllocationsCarryTheirValues() {
        MemoryStack stack = MemoryStack.create(4096);
        try (MemoryStack s = stack.push()) {
            assertEquals(16, s.malloc(16).capacity());
            assertEquals(16, s.malloc(32, 16).capacity());
            assertEquals(0, s.calloc(16).getLong(8));
            assertEquals(0, s.calloc(16, 16).getLong(8));
            assertEquals(1, s.bytes((byte) 1).get(0));
            assertEquals(2, s.bytes((byte) 1, (byte) 2).get(1));
            assertEquals(3, s.bytes((byte) 1, (byte) 2, (byte) 3).get(2));
            assertEquals(4, s.bytes((byte) 1, (byte) 2, (byte) 3, (byte) 4).get(3));
            ByteBuffer bytes = s.bytes(new byte[] {5, 6, 7, 8, 9});
            assertEquals(5, bytes.remaining());
            assertEquals(9, bytes.get(4));
            assertEquals(7, MemoryUtilities.memGetByte(s.nbyte((byte) 7)));

            assertEquals(3, s.mallocShort(3).capacity());
            assertEquals(0, s.callocShort(3).get(2));
            assertEquals(1, s.shorts((short) 1).get(0));
            assertEquals(2, s.shorts((short) 1, (short) 2).get(1));
            assertEquals(3, s.shorts((short) 1, (short) 2, (short) 3).get(2));
            assertEquals(4, s.shorts((short) 1, (short) 2, (short) 3, (short) 4).get(3));
            ShortBuffer shorts = s.shorts(new short[] {5, 6, 7, 8, 9});
            assertEquals(5, shorts.remaining());
            assertEquals(9, shorts.get(4));
            assertEquals(7, MemoryUtilities.memGetShort(s.nshort((short) 7)));

            assertEquals(3, s.mallocInt(3).capacity());
            assertEquals(0, s.callocInt(3).get(2));
            assertEquals(1, s.ints(1).get(0));
            assertEquals(2, s.ints(1, 2).get(1));
            assertEquals(3, s.ints(1, 2, 3).get(2));
            assertEquals(4, s.ints(1, 2, 3, 4).get(3));
            IntBuffer ints = s.ints(5, 6, 7, 8, 9);
            assertEquals(5, ints.remaining());
            assertEquals(9, ints.get(4));
            assertEquals(7, MemoryUtilities.memGetInt(s.nint(7)));

            assertEquals(3, s.mallocLong(3).capacity());
            assertEquals(0L, s.callocLong(3).get(2));
            assertEquals(1L, s.longs(1L).get(0));
            assertEquals(2L, s.longs(1L, 2L).get(1));
            assertEquals(3L, s.longs(1L, 2L, 3L).get(2));
            assertEquals(4L, s.longs(1L, 2L, 3L, 4L).get(3));
            LongBuffer longs = s.longs(5L, 6L, 7L, 8L, 9L);
            assertEquals(5, longs.remaining());
            assertEquals(9L, longs.get(4));
            assertEquals(7L, MemoryUtilities.memGetLong(s.nlong(7L)));

            assertEquals(3, s.mallocFloat(3).capacity());
            assertEquals(0f, s.callocFloat(3).get(2));
            assertEquals(1f, s.floats(1f).get(0));
            assertEquals(2f, s.floats(1f, 2f).get(1));
            assertEquals(3f, s.floats(1f, 2f, 3f).get(2));
            assertEquals(4f, s.floats(1f, 2f, 3f, 4f).get(3));
            FloatBuffer floats = s.floats(5f, 6f, 7f, 8f, 9f);
            assertEquals(5, floats.remaining());
            assertEquals(9f, floats.get(4));
            assertEquals(7f, MemoryUtilities.memGetFloat(s.nfloat(7f)));

            assertEquals(3, s.mallocDouble(3).capacity());
            assertEquals(0d, s.callocDouble(3).get(2));
            assertEquals(1d, s.doubles(1d).get(0));
            assertEquals(2d, s.doubles(1d, 2d).get(1));
            assertEquals(3d, s.doubles(1d, 2d, 3d).get(2));
            assertEquals(4d, s.doubles(1d, 2d, 3d, 4d).get(3));
            DoubleBuffer doubles = s.doubles(5d, 6d, 7d, 8d, 9d);
            assertEquals(5, doubles.remaining());
            assertEquals(9d, doubles.get(4));
            assertEquals(7d, MemoryUtilities.memGetDouble(s.ndouble(7d)));

            assertEquals(3, s.mallocPointer(3).capacity());
            assertEquals(0L, s.callocPointer(3).get(2));
            assertEquals(1L, s.pointers(1L).get(0));
            assertEquals(2L, s.pointers(1L, 2L).get(1));
            assertEquals(3L, s.pointers(1L, 2L, 3L).get(2));
            assertEquals(4L, s.pointers(1L, 2L, 3L, 4L).get(3));
            PointerBuffer pointers = s.pointers(5L, 6L, 7L, 8L, 9L);
            assertEquals(5, pointers.remaining());
            assertEquals(9L, pointers.get(4));
            assertEquals(7L, MemoryUtilities.memGetAddress(s.npointer(7L)));

            assertEquals(1L, s.pointers(at(1)).get(0));
            assertEquals(2L, s.pointers(at(1), at(2)).get(1));
            assertEquals(3L, s.pointers(at(1), at(2), at(3)).get(2));
            assertEquals(4L, s.pointers(at(1), at(2), at(3), at(4)).get(3));
            assertEquals(9L, s.pointers(at(5), at(6), at(7), at(8), at(9)).get(4));
            assertEquals(7L, MemoryUtilities.memGetAddress(s.npointer(at(7))));

            ByteBuffer one = s.malloc(8);
            ByteBuffer two = s.malloc(8);
            ByteBuffer three = s.malloc(8);
            ByteBuffer four = s.malloc(8);
            ByteBuffer five = s.malloc(8);
            long oneAddress = MemoryUtilities.memAddress(one);
            assertEquals(oneAddress, s.pointers(one).get(0));
            assertEquals(oneAddress, s.pointers(two, one).get(1));
            assertEquals(oneAddress, s.pointers(two, three, one).get(2));
            assertEquals(oneAddress, s.pointers(two, three, four, one).get(3));
            assertEquals(oneAddress, s.pointers(new Buffer[] {two, three, four, five, one}).get(4));
            assertEquals(oneAddress, MemoryUtilities.memGetAddress(s.npointer(one)));
        }
    }

    @Test
    void textEncodersWriteOntoTheStack() {
        MemoryStack stack = MemoryStack.create(4096);
        try (MemoryStack s = stack.push()) {
            assertEquals(3, s.ASCII("ab").capacity());
            assertEquals(2, s.ASCII("ab", false).capacity());
            assertEquals(3, s.nASCII("ab", true));
            assertEquals("ab", MemoryUtilities.memASCII(MemoryUtilities.memAddress(s.ASCIISafe("ab"))));
            assertEquals(2, s.ASCIISafe("ab", false).capacity());
            assertNull(s.ASCIISafe(null));
            assertNull(s.ASCIISafe(null, false));
            assertEquals(2, s.nASCIISafe("ab", false));
            assertEquals(0, s.nASCIISafe(null, true));

            assertEquals(4, s.UTF8("a\u00e9").capacity());
            assertEquals(3, s.UTF8("a\u00e9", false).capacity());
            assertEquals(4, s.nUTF8("a\u00e9", true));
            assertEquals("a\u00e9", MemoryUtilities.memUTF8(MemoryUtilities.memAddress(s.UTF8Safe("a\u00e9"))));
            assertEquals(3, s.UTF8Safe("a\u00e9", false).capacity());
            assertNull(s.UTF8Safe(null));
            assertNull(s.UTF8Safe(null, false));
            assertEquals(3, s.nUTF8Safe("a\u00e9", false));
            assertEquals(0, s.nUTF8Safe(null, true));

            assertEquals(6, s.UTF16("ab").capacity());
            assertEquals(4, s.UTF16("ab", false).capacity());
            assertEquals(6, s.nUTF16("ab", true));
            assertEquals("ab", MemoryUtilities.memUTF16(MemoryUtilities.memAddress(s.UTF16Safe("ab"))));
            assertEquals(4, s.UTF16Safe("ab", false).capacity());
            assertNull(s.UTF16Safe(null));
            assertNull(s.UTF16Safe(null, false));
            assertEquals(4, s.nUTF16Safe("ab", false));
            assertEquals(0, s.nUTF16Safe(null, true));
        }
    }

    @Test
    void threadLocalEntryPointsForwardToTheCurrentStack() {
        MemoryStack stack = stackGet();
        assertSame(stack, stackPush());
        assertEquals(1, stack.getFrameIndex());
        try {
            assertEquals(0, nstackMalloc(8) & 7);
            assertEquals(0, nstackMalloc(16, 8) & 15);
            long zeroed = nstackCalloc(8, 2, 8);
            assertEquals(0L, MemoryUtilities.memGetLong(zeroed + 8));
            assertEquals(8, stackMalloc(8).capacity());
            assertEquals(0, stackCalloc(8).get(7));
            assertEquals(1, stackBytes((byte) 1).get(0));
            assertEquals(2, stackBytes((byte) 1, (byte) 2).get(1));
            assertEquals(3, stackBytes((byte) 1, (byte) 2, (byte) 3).get(2));
            assertEquals(4, stackBytes((byte) 1, (byte) 2, (byte) 3, (byte) 4).get(3));
            assertEquals(5, stackBytes(new byte[] {1, 2, 3, 4, 5}).get(4));
            assertEquals(2, stackMallocShort(2).capacity());
            assertEquals(0, stackCallocShort(2).get(1));
            assertEquals(1, stackShorts((short) 1).get(0));
            assertEquals(2, stackShorts((short) 1, (short) 2).get(1));
            assertEquals(3, stackShorts((short) 1, (short) 2, (short) 3).get(2));
            assertEquals(4, stackShorts((short) 1, (short) 2, (short) 3, (short) 4).get(3));
            assertEquals(5, stackShorts(new short[] {1, 2, 3, 4, 5}).get(4));
            assertEquals(2, stackMallocInt(2).capacity());
            assertEquals(0, stackCallocInt(2).get(1));
            assertEquals(1, stackInts(1).get(0));
            assertEquals(2, stackInts(1, 2).get(1));
            assertEquals(3, stackInts(1, 2, 3).get(2));
            assertEquals(4, stackInts(1, 2, 3, 4).get(3));
            assertEquals(5, stackInts(1, 2, 3, 4, 5).get(4));
            assertEquals(2, stackMallocLong(2).capacity());
            assertEquals(0L, stackCallocLong(2).get(1));
            assertEquals(1L, stackLongs(1L).get(0));
            assertEquals(2L, stackLongs(1L, 2L).get(1));
            assertEquals(3L, stackLongs(1L, 2L, 3L).get(2));
            assertEquals(4L, stackLongs(1L, 2L, 3L, 4L).get(3));
            assertEquals(5L, stackLongs(1L, 2L, 3L, 4L, 5L).get(4));
            assertEquals(2, stackMallocFloat(2).capacity());
            assertEquals(0f, stackCallocFloat(2).get(1));
            assertEquals(1f, stackFloats(1f).get(0));
            assertEquals(2f, stackFloats(1f, 2f).get(1));
            assertEquals(3f, stackFloats(1f, 2f, 3f).get(2));
            assertEquals(4f, stackFloats(1f, 2f, 3f, 4f).get(3));
            assertEquals(5f, stackFloats(1f, 2f, 3f, 4f, 5f).get(4));
            assertEquals(2, stackMallocDouble(2).capacity());
            assertEquals(0d, stackCallocDouble(2).get(1));
            assertEquals(1d, stackDoubles(1d).get(0));
            assertEquals(2d, stackDoubles(1d, 2d).get(1));
            assertEquals(3d, stackDoubles(1d, 2d, 3d).get(2));
            assertEquals(4d, stackDoubles(1d, 2d, 3d, 4d).get(3));
            assertEquals(5d, stackDoubles(1d, 2d, 3d, 4d, 5d).get(4));
            assertEquals(2, stackMallocPointer(2).capacity());
            assertEquals(0L, stackCallocPointer(2).get(1));
            assertEquals(1L, stackPointers(1L).get(0));
            assertEquals(2L, stackPointers(1L, 2L).get(1));
            assertEquals(3L, stackPointers(1L, 2L, 3L).get(2));
            assertEquals(4L, stackPointers(1L, 2L, 3L, 4L).get(3));
            assertEquals(5L, stackPointers(1L, 2L, 3L, 4L, 5L).get(4));
            assertEquals(1L, stackPointers(at(1)).get(0));
            assertEquals(2L, stackPointers(at(1), at(2)).get(1));
            assertEquals(3L, stackPointers(at(1), at(2), at(3)).get(2));
            assertEquals(4L, stackPointers(at(1), at(2), at(3), at(4)).get(3));
            assertEquals(5L, stackPointers(at(1), at(2), at(3), at(4), at(5)).get(4));
            assertEquals(3, stackASCII("ab").capacity());
            assertEquals(2, stackASCII("ab", false).capacity());
            assertEquals(3, stackUTF8("ab").capacity());
            assertEquals(2, stackUTF8("ab", false).capacity());
            assertEquals(6, stackUTF16("ab").capacity());
            assertEquals(4, stackUTF16("ab", false).capacity());
            assertEquals(3, stackASCIISafe("ab").capacity());
            assertNull(stackASCIISafe(null, false));
            assertEquals(3, stackUTF8Safe("ab").capacity());
            assertNull(stackUTF8Safe(null, false));
            assertEquals(6, stackUTF16Safe("ab").capacity());
            assertNull(stackUTF16Safe(null, false));
        } finally {
            assertSame(stack, stackPop());
        }
        assertEquals(0, stack.getFrameIndex());
        assertEquals(stack.getSize(), stack.getPointer());
    }
}
