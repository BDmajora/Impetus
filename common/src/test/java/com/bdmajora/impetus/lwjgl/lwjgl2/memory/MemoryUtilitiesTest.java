package com.bdmajora.impetus.lwjgl.lwjgl2.memory;

import com.bdmajora.testing.Statics;
import org.junit.jupiter.api.Test;
import org.lwjgl.PointerBuffer;

import java.nio.Buffer;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;

import static com.bdmajora.impetus.lwjgl.lwjgl2.memory.MemoryUtilities.*;
import static org.junit.jupiter.api.Assertions.*;

class MemoryUtilitiesTest {
    // Text that exercises the one, two, three and four byte UTF-8 shapes
    private static final String MIXED = "a\u00e9\u20ac\ud83d\ude00z";

    // A CharSequence that only reports a length, for the length-overflow guards
    private static CharSequence lengthOnly(int length) {
        return new CharSequence() {
            @Override public int length() { return length; }
            @Override public char charAt(int index) { return 'a'; }
            @Override public CharSequence subSequence(int start, int end) { throw new UnsupportedOperationException(); }
        };
    }

    @Test
    void mathHelpersAndConstants() {
        assertTrue(mathIsPoT(64));
        assertFalse(mathIsPoT(96));
        assertEquals(64, mathRoundPoT(33));
        assertEquals(64, mathRoundPoT(64));
        assertTrue(mathHasZeroByte(0x11002233));
        assertFalse(mathHasZeroByte(0x11223344));
        assertTrue(mathHasZeroByte(0x1100223344556677L));
        assertFalse(mathHasZeroByte(0x1122334455667788L));
        assertTrue(mathHasZeroShort(0x00001234));
        assertFalse(mathHasZeroShort(0x12345678));
        assertTrue(mathHasZeroShort(0x1234000012345678L));
        assertFalse(mathHasZeroShort(0x1234567812345678L));
        assertTrue(mathIsPoT(PAGE_SIZE));
        assertEquals(64, CACHE_LINE_SIZE);
        assertEquals(0L, NULL);
        assertSame(getAllocator(), getAllocator(true));
        assertSame(getAllocator(), getAllocator(false));
        assertSame(UNSAFE, getUnsafeInstance());
        assertEquals(8, Pointer.POINTER_SIZE);
        assertEquals(3, Pointer.POINTER_SHIFT);
        assertTrue(Pointer.BITS64);
        assertFalse(Pointer.BITS32);
        assertEquals(Pointer.CLONG_SIZE == 8 ? 3 : 2, Pointer.CLONG_SHIFT);
    }

    @Test
    void stdlibAllocatorMallocsCallocsReallocsAndFrees() {
        MemoryAllocator allocator = getAllocator();
        long a = allocator.malloc(16);
        assertNotEquals(0L, a);
        long c = allocator.calloc(4, 4);
        for (int i = 0; i < 16; i++) {
            assertEquals(0, memGetByte(c + i));
        }
        memPutInt(c, 7);
        long r = allocator.realloc(c, 64);
        assertEquals(7, memGetInt(r));
        allocator.free(a);
        allocator.free(r);
        assertNotEquals(0L, nmemAllocChecked(0));
        assertNotEquals(0L, nmemCallocChecked(0, 0));
        long grown = nmemReallocChecked(NULL, 0);
        assertNotEquals(0L, grown);
        nmemFree(nmemRealloc(grown, 32));
        long zeroed = nmemCalloc(2, 8);
        assertEquals(0L, memGetLong(zeroed + 8));
        nmemFree(zeroed);
        nmemFree(nmemAlloc(8));
    }

    @Test
    void typedAllocationsWrapNativeMemory() {
        ByteBuffer b = memAlloc(10);
        assertEquals(10, b.capacity());
        assertEquals(ByteOrder.nativeOrder(), b.order());
        assertTrue(b.isDirect());
        ShortBuffer s = memAllocShort(3);
        IntBuffer i = memAllocInt(3);
        FloatBuffer f = memAllocFloat(3);
        LongBuffer l = memAllocLong(3);
        DoubleBuffer d = memAllocDouble(3);
        PointerBuffer p = memAllocPointer(3);
        assertEquals(3, s.capacity());
        assertEquals(3, i.capacity());
        assertEquals(3, f.capacity());
        assertEquals(3, l.capacity());
        assertEquals(3, d.capacity());
        assertEquals(3, p.capacity());
        memFree(b);
        memFree(s);
        memFree(i);
        memFree(f);
        memFree(l);
        memFree(d);
        memFree(p.getBuffer());

        ByteBuffer cb = memCalloc(2, 4);
        assertEquals(8, cb.capacity());
        assertEquals(0L, cb.getLong(0));
        ByteBuffer c1 = memCalloc(5);
        ShortBuffer cs = memCallocShort(2);
        IntBuffer ci = memCallocInt(2);
        FloatBuffer cf = memCallocFloat(2);
        LongBuffer cl = memCallocLong(2);
        DoubleBuffer cd = memCallocDouble(2);
        PointerBuffer cp = memCallocPointer(2);
        assertEquals(0, c1.get(4));
        assertEquals(0, cs.get(1));
        assertEquals(0, ci.get(1));
        assertEquals(0f, cf.get(1));
        assertEquals(0L, cl.get(1));
        assertEquals(0d, cd.get(1));
        assertEquals(0L, cp.get(1));
        CharBuffer cc = memCharBuffer(memAddress(c1), 2);
        memFree((Buffer) cb);
        memFree(cc);
        memFree(cs);
        memFree(ci);
        memFree(cf);
        memFree(cl);
        memFree(cd);
        memFree(cp.getBuffer());
        // Every overload tolerates null
        memFree((Buffer) null);
        memFree((ByteBuffer) null);
        memFree((ShortBuffer) null);
        memFree((CharBuffer) null);
        memFree((IntBuffer) null);
        memFree((LongBuffer) null);
        memFree((FloatBuffer) null);
        memFree((DoubleBuffer) null);
    }

    @Test
    void reallocKeepsContentAndClampsPosition() {
        ByteBuffer b = memRealloc((ByteBuffer) null, 8);
        b.put(0, (byte) 9).position(6);
        b = memRealloc(b, 4);
        assertEquals(9, b.get(0));
        assertEquals(4, b.position());
        memFree(b);

        ShortBuffer s = memRealloc((ShortBuffer) null, 4);
        s.put(0, (short) 3).position(2);
        s = memRealloc(s, 8);
        assertEquals(3, s.get(0));
        assertEquals(2, s.position());
        memFree(s);

        IntBuffer i = memRealloc((IntBuffer) null, 4);
        i.put(1, 5);
        i = memRealloc(i, 2);
        assertEquals(5, i.get(1));
        memFree(i);

        LongBuffer l = memRealloc((LongBuffer) null, 2);
        l.put(0, 11L);
        l = memRealloc(l, 3);
        assertEquals(11L, l.get(0));
        memFree(l);

        FloatBuffer f = memRealloc((FloatBuffer) null, 2);
        f.put(0, 1.5f);
        f = memRealloc(f, 3);
        assertEquals(1.5f, f.get(0));
        memFree(f);

        DoubleBuffer d = memRealloc((DoubleBuffer) null, 2);
        d.put(0, 2.5);
        d = memRealloc(d, 1);
        assertEquals(2.5, d.get(0));
        memFree(d);

        PointerBuffer p = memRealloc((PointerBuffer) null, 2);
        p.put(0, 0x1234L).position(2);
        p = memRealloc(p, 1);
        assertEquals(0x1234L, p.get(0));
        assertEquals(1, p.position());
        memFree(p.getBuffer());
    }

    @Test
    void addressesAccountForPositionAndElementSize() {
        ByteBuffer b = memAlloc(64);
        long base = memAddress0(b);
        assertEquals(base, memAddress0((Buffer) b));
        assertEquals(base, memAddress(b));
        assertEquals(base + 3, memAddress(b, 3));
        b.position(2);
        assertEquals(base + 2, memAddress(b));
        assertEquals(base + 2, memAddress((Buffer) b));
        assertEquals(base + 2, memAddressSafe(b));
        b.position(0);

        ShortBuffer s = b.asShortBuffer();
        assertEquals(base, memAddress0(s));
        s.position(1);
        assertEquals(base + 2, memAddress(s));
        assertEquals(base + 4, memAddress(s, 2));
        assertEquals(base + 2, memAddress((Buffer) s));
        assertEquals(base + 2, memAddressSafe(s));

        CharBuffer c = b.asCharBuffer();
        assertEquals(base, memAddress0(c));
        c.position(1);
        assertEquals(base + 2, memAddress(c));
        assertEquals(base + 4, memAddress(c, 2));
        assertEquals(base + 2, memAddress((Buffer) c));
        assertEquals(base + 2, memAddressSafe(c));

        IntBuffer i = b.asIntBuffer();
        assertEquals(base, memAddress0(i));
        i.position(1);
        assertEquals(base + 4, memAddress(i));
        assertEquals(base + 8, memAddress(i, 2));
        assertEquals(base + 4, memAddress((Buffer) i));
        assertEquals(base + 4, memAddressSafe(i));

        FloatBuffer f = b.asFloatBuffer();
        assertEquals(base, memAddress0(f));
        f.position(1);
        assertEquals(base + 4, memAddress(f));
        assertEquals(base + 8, memAddress(f, 2));
        assertEquals(base + 4, memAddress((Buffer) f));
        assertEquals(base + 4, memAddressSafe(f));

        LongBuffer l = b.asLongBuffer();
        assertEquals(base, memAddress0(l));
        l.position(1);
        assertEquals(base + 8, memAddress(l));
        assertEquals(base + 16, memAddress(l, 2));
        assertEquals(base + 8, memAddress((Buffer) l));
        assertEquals(base + 8, memAddressSafe(l));

        DoubleBuffer d = b.asDoubleBuffer();
        assertEquals(base, memAddress0(d));
        d.position(1);
        assertEquals(base + 8, memAddress(d));
        assertEquals(base + 16, memAddress(d, 2));
        assertEquals(base + 8, memAddress((Buffer) d));
        assertEquals(base + 8, memAddressSafe(d));

        PointerBuffer p = memPointerBuffer(base, 4);
        p.position(1);
        assertEquals(base + 8, memAddress(p));
        assertEquals(base + 16, memAddress(p, 2));

        assertEquals(NULL, memAddressSafe((ByteBuffer) null));
        assertEquals(NULL, memAddressSafe((ShortBuffer) null));
        assertEquals(NULL, memAddressSafe((CharBuffer) null));
        assertEquals(NULL, memAddressSafe((IntBuffer) null));
        assertEquals(NULL, memAddressSafe((FloatBuffer) null));
        assertEquals(NULL, memAddressSafe((LongBuffer) null));
        assertEquals(NULL, memAddressSafe((DoubleBuffer) null));
        assertEquals(NULL, memAddressSafe((Pointer) null));
        assertEquals(base, memAddressSafe((Pointer) () -> base));
        memFree(b);
    }

    @Test
    void wrappersViewNativeMemoryAndRejectNull() {
        ByteBuffer b = memCalloc(64);
        long base = memAddress(b);
        assertEquals(16, memByteBuffer(base, 16).capacity());
        assertEquals(16, memByteBufferSafe(base, 16).capacity());
        assertNull(memByteBufferSafe(NULL, 16));
        assertThrows(IllegalArgumentException.class, () -> memByteBuffer(NULL, 16));

        ShortBuffer s = memShortBuffer(base, 4);
        CharBuffer c = memCharBuffer(base, 4);
        IntBuffer i = memIntBuffer(base, 4);
        LongBuffer l = memLongBuffer(base, 4);
        FloatBuffer f = memFloatBuffer(base, 4);
        DoubleBuffer d = memDoubleBuffer(base, 4);
        PointerBuffer p = memPointerBuffer(base, 4);
        assertEquals(4, s.capacity());
        assertEquals(4, c.capacity());
        assertEquals(4, i.capacity());
        assertEquals(4, l.capacity());
        assertEquals(4, f.capacity());
        assertEquals(4, d.capacity());
        assertEquals(4, p.capacity());
        assertEquals(4, memShortBufferSafe(base, 4).capacity());
        assertEquals(4, memCharBufferSafe(base, 4).capacity());
        assertEquals(4, memIntBufferSafe(base, 4).capacity());
        assertEquals(4, memLongBufferSafe(base, 4).capacity());
        assertEquals(4, memFloatBufferSafe(base, 4).capacity());
        assertEquals(4, memDoubleBufferSafe(base, 4).capacity());
        assertEquals(4, memPointerBufferSafe(base, 4).capacity());
        assertNull(memShortBufferSafe(NULL, 4));
        assertNull(memCharBufferSafe(NULL, 4));
        assertNull(memIntBufferSafe(NULL, 4));
        assertNull(memLongBufferSafe(NULL, 4));
        assertNull(memFloatBufferSafe(NULL, 4));
        assertNull(memDoubleBufferSafe(NULL, 4));
        assertNull(memPointerBufferSafe(NULL, 4));
        assertThrows(IllegalArgumentException.class, () -> memShortBuffer(NULL, 4));
        assertThrows(IllegalArgumentException.class, () -> memCharBuffer(NULL, 4));
        assertThrows(IllegalArgumentException.class, () -> memIntBuffer(NULL, 4));
        assertThrows(IllegalArgumentException.class, () -> memLongBuffer(NULL, 4));
        assertThrows(IllegalArgumentException.class, () -> memFloatBuffer(NULL, 4));
        assertThrows(IllegalArgumentException.class, () -> memDoubleBuffer(NULL, 4));
        assertThrows(IllegalArgumentException.class, () -> memPointerBuffer(NULL, 4));

        // Byte views of typed buffers cover the remaining elements from the current position
        s.position(1);
        assertEquals(6, memByteBuffer(s).capacity());
        assertEquals(base + 2, memAddress(memByteBuffer(s)));
        c.position(1);
        assertEquals(6, memByteBuffer(c).capacity());
        i.position(1);
        assertEquals(12, memByteBuffer(i).capacity());
        l.position(1);
        assertEquals(24, memByteBuffer(l).capacity());
        f.position(1);
        assertEquals(12, memByteBuffer(f).capacity());
        d.position(1);
        assertEquals(24, memByteBuffer(d).capacity());
        memFree(b);
    }

    @Test
    void duplicatesShareMemoryAndKeepParents() {
        ByteBuffer b = memAlloc(32);
        b.position(4).limit(20);
        ByteBuffer dup = memDuplicate(b);
        assertEquals(4, dup.position());
        assertEquals(20, dup.limit());
        assertEquals(32, dup.capacity());
        assertEquals(memAddress0(b), memAddress0(dup));
        assertEquals(b.order(), dup.order());
        // Duplicating a duplicate keeps pointing at the root buffer
        ByteBuffer dup2 = memDuplicate(dup);
        assertEquals(memAddress0(b), memAddress0(dup2));
        b.clear();

        ShortBuffer s = b.asShortBuffer();
        s.position(2);
        assertEquals(2, memDuplicate(s).position());
        CharBuffer c = b.asCharBuffer();
        c.position(2);
        assertEquals(2, memDuplicate(c).position());
        IntBuffer i = b.asIntBuffer();
        i.position(2);
        assertEquals(2, memDuplicate(i).position());
        LongBuffer l = b.asLongBuffer();
        l.position(2);
        assertEquals(2, memDuplicate(l).position());
        FloatBuffer f = b.asFloatBuffer();
        f.position(2);
        assertEquals(2, memDuplicate(f).position());
        DoubleBuffer d = b.asDoubleBuffer();
        d.position(2);
        assertEquals(2, memDuplicate(d).position());
        memFree(b);
    }

    @Test
    void slicesStartAtThePositionOrAnOffset() {
        ByteBuffer b = memAlloc(64);
        long base = memAddress0(b);
        b.position(8).limit(40);
        ByteBuffer sb = memSlice(b);
        assertEquals(base + 8, memAddress0(sb));
        assertEquals(32, sb.capacity());
        assertEquals(base + 12, memAddress0(memSlice(memSlice(b), 4, 8)));
        ByteBuffer ob = memSlice(b, 4, 8);
        assertEquals(base + 12, memAddress0(ob));
        assertEquals(8, ob.capacity());
        assertThrows(IllegalArgumentException.class, () -> memSlice(b, -1, 8));
        assertThrows(IllegalArgumentException.class, () -> memSlice(b, 40, 8));
        assertThrows(IllegalArgumentException.class, () -> memSlice(b, 4, -1));
        assertThrows(IllegalArgumentException.class, () -> memSlice(b, 4, 60));
        b.clear();

        ShortBuffer s = b.asShortBuffer();
        s.position(2);
        assertEquals(base + 4, memAddress0(memSlice(s)));
        assertEquals(base + 6, memAddress0(memSlice(s, 1, 4)));
        assertEquals(4, memSlice(s, 1, 4).capacity());
        assertThrows(IllegalArgumentException.class, () -> memSlice(s, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> memSlice(s, 1, 100));
        CharBuffer c = b.asCharBuffer();
        c.position(2);
        assertEquals(base + 4, memAddress0(memSlice(c)));
        assertEquals(base + 6, memAddress0(memSlice(c, 1, 4)));
        assertThrows(IllegalArgumentException.class, () -> memSlice(c, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> memSlice(c, 1, 100));
        IntBuffer i = b.asIntBuffer();
        i.position(2);
        assertEquals(base + 8, memAddress0(memSlice(i)));
        assertEquals(base + 12, memAddress0(memSlice(i, 1, 4)));
        assertThrows(IllegalArgumentException.class, () -> memSlice(i, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> memSlice(i, 1, 100));
        LongBuffer l = b.asLongBuffer();
        l.position(2);
        assertEquals(base + 16, memAddress0(memSlice(l)));
        assertEquals(base + 24, memAddress0(memSlice(l, 1, 4)));
        assertThrows(IllegalArgumentException.class, () -> memSlice(l, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> memSlice(l, 1, 100));
        FloatBuffer f = b.asFloatBuffer();
        f.position(2);
        assertEquals(base + 8, memAddress0(memSlice(f)));
        assertEquals(base + 12, memAddress0(memSlice(f, 1, 4)));
        assertThrows(IllegalArgumentException.class, () -> memSlice(f, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> memSlice(f, 1, 100));
        DoubleBuffer d = b.asDoubleBuffer();
        d.position(2);
        assertEquals(base + 16, memAddress0(memSlice(d)));
        assertEquals(base + 24, memAddress0(memSlice(d, 1, 4)));
        assertThrows(IllegalArgumentException.class, () -> memSlice(d, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> memSlice(d, 1, 100));
        memFree(b);
    }

    @Test
    void setAndCopyCoverEveryElementType() {
        ByteBuffer src = memCalloc(512);
        ByteBuffer dst = memCalloc(512);
        long s = memAddress(src);
        long d = memAddress(dst);

        // Short aligned fills take the unrolled path, unaligned and long ones go through Unsafe
        memSet(s, 0xAB, 13);
        assertEquals((byte) 0xAB, memGetByte(s + 12));
        assertEquals(0, memGetByte(s + 13));
        memSet(s + 1, 0xCD, 5);
        assertEquals((byte) 0xCD, memGetByte(s + 5));
        assertEquals((byte) 0xAB, memGetByte(s + 6));
        memSet(s, 0x11, 300);
        assertEquals((byte) 0x11, memGetByte(s + 299));
        assertEquals(0, memGetByte(s + 300));
        // The 32-bit fill and copy only ever run their loop guards on a 64-bit JVM
        Statics.call(MemoryUtilities.class, "memSet32", 0, 0, 0);
        memCopyAligned32(0, 0, 0);
        Statics.call(MemoryUtilities.class, "memSet64", s, 0x22, 11);
        assertEquals((byte) 0x22, memGetByte(s + 10));
        memCopyAligned64(s, d, 11);
        assertEquals((byte) 0x22, memGetByte(d + 10));
        assertEquals(0, memGetByte(d + 11));
        memCopy(s, d + 100, 20);
        assertEquals((byte) 0x22, memGetByte(d + 110));

        src.clear();
        dst.clear();
        memSet(src, 0x33);
        assertEquals((byte) 0x33, src.get(511));
        memCopy(src, dst);
        assertEquals((byte) 0x33, dst.get(511));

        ShortBuffer ss = src.asShortBuffer();
        ShortBuffer ds = dst.asShortBuffer();
        memSet(ss, 0x44);
        assertEquals((short) 0x4444, ss.get(255));
        memCopy(ss, ds);
        assertEquals((short) 0x4444, ds.get(255));

        CharBuffer sc = src.asCharBuffer();
        CharBuffer dc = dst.asCharBuffer();
        memSet(sc, 0x55);
        assertEquals((char) 0x5555, sc.get(255));
        memCopy(sc, dc);
        assertEquals((char) 0x5555, dc.get(255));

        IntBuffer si = src.asIntBuffer();
        IntBuffer di = dst.asIntBuffer();
        memSet(si, 0x66);
        assertEquals(0x66666666, si.get(127));
        memCopy(si, di);
        assertEquals(0x66666666, di.get(127));

        LongBuffer sl = src.asLongBuffer();
        LongBuffer dl = dst.asLongBuffer();
        memSet(sl, 0x77);
        assertEquals(0x7777777777777777L, sl.get(63));
        memCopy(sl, dl);
        assertEquals(0x7777777777777777L, dl.get(63));

        FloatBuffer sf = src.asFloatBuffer();
        FloatBuffer df = dst.asFloatBuffer();
        memSet(sf, 0);
        assertEquals(0f, sf.get(127));
        memCopy(sf, df);
        assertEquals(0f, df.get(127));

        DoubleBuffer sd = src.asDoubleBuffer();
        DoubleBuffer dd = dst.asDoubleBuffer();
        memSet(sd, 0);
        assertEquals(0d, sd.get(63));
        memCopy(sd, dd);
        assertEquals(0d, dd.get(63));
        memFree(src);
        memFree(dst);
    }

    @Test
    void scalarGettersAndSettersRoundTrip() {
        long p = nmemCalloc(1, 64);
        memPutByte(p, (byte) 5);
        assertEquals(5, memGetByte(p));
        assertTrue(memGetBoolean(p));
        memPutByte(p, (byte) 0);
        assertFalse(memGetBoolean(p));
        memPutShort(p, (short) -7);
        assertEquals(-7, memGetShort(p));
        memPutInt(p, 123456);
        assertEquals(123456, memGetInt(p));
        memPutLong(p, 1L << 40);
        assertEquals(1L << 40, memGetLong(p));
        memPutFloat(p, 2.5f);
        assertEquals(2.5f, memGetFloat(p));
        memPutDouble(p, 3.25);
        assertEquals(3.25, memGetDouble(p));
        memPutCLong(p, 99L);
        assertEquals(99L, memGetCLong(p));
        memPutAddress(p, p);
        assertEquals(p, memGetAddress(p));
        nmemFree(p);
    }

    @Test
    void asciiCodecEncodesAndDecodes() {
        ByteBuffer alloc = memASCII("hi");
        assertEquals(3, alloc.capacity());
        assertEquals('h', alloc.get(0));
        assertEquals(0, alloc.get(2));
        memFree(alloc);
        // Terminator scans read whole words, so decoding happens from a roomy scratch buffer
        ByteBuffer nt = memCalloc(16);
        memASCII("hi", true, nt);
        assertEquals("hi", memASCII(nt.limit(2)));
        nt.clear();
        assertEquals("hi", memASCII(memAddress(nt)));
        assertEquals("hi", memASCIISafe(memAddress(nt)));
        assertEquals("h", memASCIISafe(memAddress(nt), 1));
        assertEquals("i", memASCII(nt, 1, 1));
        assertEquals("hi", memASCII(nt, 2));
        nt.limit(2);
        assertEquals("hi", memASCIISafe(nt));
        nt.clear();
        assertNull(memASCIISafe((ByteBuffer) null));
        assertNull(memASCIISafe(NULL));
        assertNull(memASCIISafe(NULL, 2));
        assertEquals("", memASCII(memAddress(nt), 0));
        memFree(nt);

        assertNull(memASCIISafe((CharSequence) null));
        assertNull(memASCIISafe(null, false));
        ByteBuffer plain = memASCIISafe("abc", false);
        assertEquals(3, plain.capacity());
        memFree(plain);
        memFree(memASCIISafe("abc"));
        assertEquals(3, memLengthASCII("abc", false));
        assertEquals(4, memLengthASCII("abc", true));
        assertThrows(BufferOverflowException.class, () -> memLengthASCII(lengthOnly(Integer.MAX_VALUE), true));

        ByteBuffer target = memCalloc(8);
        assertEquals(4, memASCII("abc", true, target));
        assertEquals("abc", memASCII(memAddress(target)));
        assertEquals(2, memASCII("xy", false, target, 4));
        assertEquals("xy", memASCII(target, 2, 4));
        target.position(6);
        assertThrows(BufferOverflowException.class, () -> memASCII("abc", true, target));
        assertThrows(BufferOverflowException.class, () -> memASCII("abc", true, target, 6));
        memFree(target);

        // Beyond the thread-local scratch size a fresh array is used
        ByteBuffer big = memCalloc(ARRAY_TLC_SIZE + 10);
        memSet(memAddress(big), 'q', ARRAY_TLC_SIZE + 1);
        String decoded = memASCII(memAddress(big), ARRAY_TLC_SIZE + 1);
        assertEquals(ARRAY_TLC_SIZE + 1, decoded.length());
        assertEquals(ARRAY_TLC_SIZE + 1, memASCII(big).length() - 9);
        memFree(big);
    }

    @Test
    void utf8CodecHandlesEveryByteShape() {
        byte[] expected = MIXED.getBytes(StandardCharsets.UTF_8);
        assertEquals(expected.length, memLengthUTF8(MIXED, false));
        assertEquals(expected.length + 1, memLengthUTF8(MIXED, true));
        ByteBuffer alloc = memUTF8(MIXED);
        assertEquals(expected.length + 1, alloc.capacity());
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], alloc.get(i));
        }
        assertEquals(0, alloc.get(expected.length));
        memFree(alloc);
        ByteBuffer nt = memCalloc(32);
        memUTF8(MIXED, true, nt);
        assertEquals(MIXED, memUTF8(memAddress(nt)));
        assertEquals(MIXED, memUTF8Safe(memAddress(nt)));
        assertEquals(MIXED, memUTF8(memAddress(nt), expected.length));
        assertEquals(MIXED, memUTF8Safe(memAddress(nt), expected.length));
        assertEquals(MIXED, memUTF8(nt, expected.length));
        assertEquals("z", memUTF8(nt, 1, expected.length - 1));
        nt.limit(expected.length);
        assertEquals(MIXED, memUTF8(nt));
        assertEquals(MIXED, memUTF8Safe(nt));
        assertNull(memUTF8Safe((ByteBuffer) null));
        assertNull(memUTF8Safe(NULL));
        assertNull(memUTF8Safe(NULL, 3));
        assertEquals("", memUTF8(memAddress(nt), 0));
        memFree(nt);

        assertNull(memUTF8Safe((CharSequence) null));
        assertNull(memUTF8Safe(null, false));
        memFree(memUTF8Safe("ok"));
        ByteBuffer plain = memUTF8Safe("ok", false);
        assertEquals(2, plain.capacity());
        memFree(plain);

        // Buffer targets take the checked encoder: an ASCII fast path, then a slow path that can overflow
        ByteBuffer target = memCalloc(32);
        assertEquals(expected.length + 1, memUTF8(MIXED, true, target));
        assertEquals(MIXED, memUTF8(memAddress(target)));
        assertEquals(2, memUTF8("ab", false, target, 20));
        assertEquals("ab", memUTF8(target, 2, 20));
        ByteBuffer tight = memCalloc(3);
        assertEquals(3, memUTF8("ab", true, tight));
        assertThrows(BufferOverflowException.class, () -> memUTF8("a\u00e9", true, tight));
        assertThrows(BufferOverflowException.class, () -> memUTF8("abcd", true, tight));
        assertThrows(BufferOverflowException.class, () -> memUTF8("abcd", true, tight, 0));
        assertThrows(BufferOverflowException.class, () -> memUTF8("a\u00e9", true, tight, 0));
        memFree(tight);
        memFree(target);

        // Long strings decode through a fresh array rather than the thread-local scratch
        ByteBuffer big = memCalloc(ARRAY_TLC_SIZE + 10);
        memSet(memAddress(big), 'q', ARRAY_TLC_SIZE + 1);
        assertEquals(ARRAY_TLC_SIZE + 1, memUTF8(memAddress(big), ARRAY_TLC_SIZE + 1).length());
        memFree(big);
    }

    @Test
    void utf16CodecWritesTwoBytesPerChar() {
        assertEquals(6, memLengthUTF16("abc", false));
        assertEquals(8, memLengthUTF16("abc", true));
        assertThrows(BufferOverflowException.class, () -> memLengthUTF16(lengthOnly(0x40000000), false));
        ByteBuffer alloc = memUTF16("h\u20ac");
        assertEquals(6, alloc.capacity());
        assertEquals('h', alloc.getChar(0));
        assertEquals('\u20ac', alloc.getChar(2));
        assertEquals(0, alloc.getChar(4));
        memFree(alloc);
        ByteBuffer nt = memCalloc(16);
        memUTF16("h\u20ac", true, nt);
        assertEquals("h\u20ac", memUTF16(memAddress(nt)));
        assertEquals("h\u20ac", memUTF16Safe(memAddress(nt)));
        assertEquals("h", memUTF16(memAddress(nt), 1));
        assertEquals("h", memUTF16Safe(memAddress(nt), 1));
        assertEquals("h\u20ac", memUTF16(nt, 2));
        assertEquals("\u20ac", memUTF16(nt, 1, 2));
        nt.limit(4);
        assertEquals("h\u20ac", memUTF16(nt));
        assertEquals("h\u20ac", memUTF16Safe(nt));
        assertNull(memUTF16Safe((ByteBuffer) null));
        assertNull(memUTF16Safe(NULL));
        assertNull(memUTF16Safe(NULL, 1));
        assertEquals("", memUTF16(memAddress(nt), 0));
        memFree(nt);

        assertNull(memUTF16Safe((CharSequence) null));
        assertNull(memUTF16Safe(null, false));
        memFree(memUTF16Safe("ok"));
        ByteBuffer plain = memUTF16Safe("ok", false);
        assertEquals(4, plain.capacity());
        memFree(plain);

        ByteBuffer target = memCalloc(32);
        assertEquals(8, memUTF16("abc", true, target));
        assertEquals("abc", memUTF16(memAddress(target)));
        assertEquals(4, memUTF16("xy", false, target, 16));
        assertEquals("xy", memUTF16(target, 2, 16));
        target.position(30);
        assertThrows(BufferOverflowException.class, () -> memUTF16("abc", true, target));
        assertThrows(BufferOverflowException.class, () -> memUTF16("abc", true, target, 30));
        memFree(target);

        ByteBuffer big = memCalloc((ARRAY_TLC_SIZE + 10) * 2);
        memSet(memAddress(big), 0, (ARRAY_TLC_SIZE + 10) * 2);
        assertEquals(ARRAY_TLC_SIZE + 1, memUTF16(memAddress(big), ARRAY_TLC_SIZE + 1).length());
        memFree(big);
    }

    @Test
    void nullTerminatedLengthsScanBothWidths() {
        ByteBuffer b = memCalloc(64);
        long base = memAddress(b);
        memASCII("hello world", true, b);
        assertEquals(11, memLengthNT1(b));
        assertEquals(11, memByteBufferNT1(base).capacity());
        assertEquals(11, memByteBufferNT1(base, 64).capacity());
        assertEquals(11, memByteBufferNT1Safe(base).capacity());
        assertEquals(11, memByteBufferNT1Safe(base, 64).capacity());
        assertEquals(5, memByteBufferNT1(base, 5).capacity());
        assertNull(memByteBufferNT1Safe(NULL));
        assertNull(memByteBufferNT1Safe(NULL, 4));
        // A misaligned start scans byte-wise up to the next word, finding an early terminator or not
        assertEquals(10, memByteBufferNT1(base + 1).capacity());
        assertEquals("orld", memASCII(base + 7));
        memPutByte(base + 9, (byte) 0);
        assertEquals("r", memASCII(base + 8));
        assertEquals("", memASCII(base + 9));
        assertEquals(1, (int) Statics.call(MemoryUtilities.class, "strlen32NT1", base + 8, 64));
        assertEquals(0, (int) Statics.call(MemoryUtilities.class, "strlen32NT1", base + 9, 64));
        assertEquals(7, (int) Statics.call(MemoryUtilities.class, "strlen32NT1", base + 2, 64));
        assertEquals(9, (int) Statics.call(MemoryUtilities.class, "strlen32NT1", base, 64));
        assertEquals(3, (int) Statics.call(MemoryUtilities.class, "strlen32NT1", base, 3));
        assertThrows(IllegalArgumentException.class, () -> Statics.call(MemoryUtilities.class, "memLengthNT1", NULL, 4));

        memSet(base, 0, 64);
        memUTF16("wide text", true, b);
        assertEquals(18, memLengthNT2(b));
        assertEquals(18, memByteBufferNT2(base).capacity());
        assertEquals(18, memByteBufferNT2(base, 64).capacity());
        assertEquals(18, memByteBufferNT2Safe(base).capacity());
        assertEquals(18, memByteBufferNT2Safe(base, 64).capacity());
        assertEquals(6, memByteBufferNT2(base, 6).capacity());
        assertNull(memByteBufferNT2Safe(NULL));
        assertNull(memByteBufferNT2Safe(NULL, 4));
        assertEquals("ide text", memUTF16(base + 2));
        memPutShort(base + 4, (short) 0);
        assertEquals("i", memUTF16(base + 2));
        assertEquals(2, (int) Statics.call(MemoryUtilities.class, "strlen32NT2", base + 2, 64));
        assertEquals(4, (int) Statics.call(MemoryUtilities.class, "strlen32NT2", base, 64));
        assertEquals(2, (int) Statics.call(MemoryUtilities.class, "strlen32NT2", base, 2));
        memPutShort(base + 4, (short) 'd');
        assertEquals(12, (int) Statics.call(MemoryUtilities.class, "strlen32NT2", base + 6, 64));
        assertThrows(IllegalArgumentException.class, () -> Statics.call(MemoryUtilities.class, "memLengthNT2", NULL, 4));
        memFree(b);
    }

    @Test
    void pointerDefaultsCompareByAddress() {
        Pointer.Default p = new Pointer.Default(0x10L) {};
        Pointer.Default same = new Pointer.Default(0x10L) {};
        Pointer.Default other = new Pointer.Default(0x20L) {};
        assertEquals(0x10L, p.address());
        assertEquals(p, p);
        assertEquals(p, same);
        assertNotEquals(p, other);
        assertNotEquals(p, "not a pointer");
        assertEquals(same.hashCode(), p.hashCode());
        assertTrue(p.toString().endsWith("pointer [0x10]"));
        assertThrows(NullPointerException.class, () -> new Pointer.Default(NULL) {});
    }
}
