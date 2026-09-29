package com.bdmajora.testing;

import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.nio.FloatBuffer;

// Unsafe-backed implementations of LWJGLService's memory entry points, so engine code that writes native memory really does in tests
public final class TestNativeMemory {
    static final Unsafe UNSAFE = unsafe();
    private static final Field ADDRESS = addressField();
    private static final Constructor<? extends ByteBuffer> DIRECT = directConstructor();

    private static Unsafe unsafe() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Field addressField() {
        try {
            Field field = Buffer.class.getDeclaredField("address");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("run tests with --add-opens java.base/java.nio=ALL-UNNAMED", e);
        }
    }

    // JDK 21 declares DirectByteBuffer(long, int); newer JDKs widened the capacity to long
    @SuppressWarnings("unchecked")
    private static Constructor<? extends ByteBuffer> directConstructor() {
        try {
            Class<? extends ByteBuffer> type = (Class<? extends ByteBuffer>) Class.forName("java.nio.DirectByteBuffer");
            Constructor<? extends ByteBuffer> ctor;
            try {
                ctor = type.getDeclaredConstructor(long.class, int.class);
            } catch (NoSuchMethodException e) {
                ctor = type.getDeclaredConstructor(long.class, long.class);
            }
            ctor.setAccessible(true);
            return ctor;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("run tests with --add-opens java.base/java.nio=ALL-UNNAMED", e);
        }
    }

    // Wraps raw memory as a native-order direct buffer without copying
    public static ByteBuffer wrap(long address, int capacity) {
        try {
            Object[] args = DIRECT.getParameterTypes()[1] == int.class
                    ? new Object[]{address, capacity}
                    : new Object[]{address, (long) capacity};
            return DIRECT.newInstance(args).order(ByteOrder.nativeOrder());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static long baseAddress(Buffer buffer) {
        try {
            return ADDRESS.getLong(buffer);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static int elementSize(Buffer buffer) {
        if (buffer instanceof ByteBuffer) return 1;
        if (buffer instanceof ShortBuffer) return 2;
        if (buffer instanceof IntBuffer || buffer instanceof FloatBuffer) return 4;
        if (buffer instanceof LongBuffer || buffer instanceof DoubleBuffer) return 8;
        return 2;
    }

    public long nmemAlloc(long size) {
        return UNSAFE.allocateMemory(Math.max(size, 1));
    }

    public long nmemCalloc(long count, long size) {
        long address = nmemAlloc(count * size);
        UNSAFE.setMemory(address, Math.max(count * size, 1), (byte) 0);
        return address;
    }

    public long nmemAlignedAlloc(long alignment, long size) {
        return nmemAlloc(size);
    }

    public long nmemRealloc(long ptr, long size) {
        return ptr == 0 ? nmemAlloc(size) : UNSAFE.reallocateMemory(ptr, Math.max(size, 1));
    }

    public void nmemFree(long ptr) {
        if (ptr != 0) {
            UNSAFE.freeMemory(ptr);
        }
    }

    public void nmemAlignedFree(long ptr) {
        nmemFree(ptr);
    }

    public ByteBuffer memAlloc(int size) {
        return wrap(nmemAlloc(size), size);
    }

    public ByteBuffer memCalloc(int size) {
        return wrap(nmemCalloc(1, size), size);
    }

    public ByteBuffer memRealloc(ByteBuffer buffer, int size) {
        long address = buffer == null ? 0 : baseAddress(buffer);
        return wrap(nmemRealloc(address, size), size);
    }

    public void memFree(Buffer buffer) {
        if (buffer != null) {
            nmemFree(baseAddress(buffer));
        }
    }

    public ByteBuffer memByteBuffer(long address, int capacity) {
        return wrap(address, capacity);
    }

    public long memAddress(Buffer buffer) {
        return memAddress(buffer, buffer.position());
    }

    public long memAddress(Buffer buffer, int position) {
        return baseAddress(buffer) + (long) position * elementSize(buffer);
    }

    public void memSet(long address, int value, long bytes) {
        UNSAFE.setMemory(address, bytes, (byte) value);
    }

    public void memCopy(long src, long dst, long bytes) {
        UNSAFE.copyMemory(src, dst, bytes);
    }

    public void memPutByte(long address, byte value) {
        UNSAFE.putByte(address, value);
    }

    public void memPutShort(long address, short value) {
        UNSAFE.putShort(address, value);
    }

    public void memPutInt(long address, int value) {
        UNSAFE.putInt(address, value);
    }

    public void memPutFloat(long address, float value) {
        UNSAFE.putFloat(address, value);
    }

    public void memPutLong(long address, long value) {
        UNSAFE.putLong(address, value);
    }

    public void memPutAddress(long address, long value) {
        UNSAFE.putLong(address, value);
    }

    public byte memGetByte(long address) {
        return UNSAFE.getByte(address);
    }

    public short memGetShort(long address) {
        return UNSAFE.getShort(address);
    }

    public int memGetInt(long address) {
        return UNSAFE.getInt(address);
    }

    public float memGetFloat(long address) {
        return UNSAFE.getFloat(address);
    }

    public long memGetLong(long address) {
        return UNSAFE.getLong(address);
    }

    public long memGetAddress(long address) {
        return UNSAFE.getLong(address);
    }

    public ByteBuffer memSlice(ByteBuffer buffer, int offset, int capacity) {
        return wrap(baseAddress(buffer) + offset, capacity);
    }
}
