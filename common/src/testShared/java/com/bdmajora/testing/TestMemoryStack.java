package com.bdmajora.testing;

import com.bdmajora.impetus.lwjgl.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

// A 64 KiB Unsafe-backed frame that grows downward like LWJGL's stack and is freed on close
public final class TestMemoryStack extends MemoryStack {
    private static final int SIZE = 1 << 16;
    private final long base = TestNativeMemory.UNSAFE.allocateMemory(SIZE);
    private long pointer = SIZE;

    @Override
    public void close() {
        TestNativeMemory.UNSAFE.freeMemory(base);
    }

    @Override
    public long getPointer() {
        return pointer;
    }

    @Override
    public void setPointer(long pointer) {
        this.pointer = pointer;
    }

    @Override
    public long getAddress() {
        return base;
    }

    @Override
    public int getSize() {
        return SIZE;
    }

    @Override
    public ByteBuffer malloc(int size) {
        return TestNativeMemory.wrap(nmalloc(size), size);
    }

    @Override
    public ShortBuffer mallocShort(int count) {
        return malloc(count * 2).asShortBuffer();
    }

    @Override
    public IntBuffer mallocInt(int count) {
        return malloc(count * 4).asIntBuffer();
    }

    @Override
    public LongBuffer mallocLong(int count) {
        return malloc(count * 8).asLongBuffer();
    }

    @Override
    public FloatBuffer mallocFloat(int count) {
        return malloc(count * 4).asFloatBuffer();
    }

    @Override
    public ByteBuffer calloc(int size) {
        return TestNativeMemory.wrap(ncalloc(8, 1, size), size);
    }

    @Override
    public ShortBuffer callocShort(int count) {
        return calloc(count * 2).asShortBuffer();
    }

    @Override
    public IntBuffer callocInt(int count) {
        return calloc(count * 4).asIntBuffer();
    }

    @Override
    public LongBuffer callocLong(int count) {
        return calloc(count * 8).asLongBuffer();
    }

    @Override
    public FloatBuffer callocFloat(int count) {
        return calloc(count * 4).asFloatBuffer();
    }

    @Override
    public long nmalloc(int size) {
        return nmalloc(8, size);
    }

    @Override
    public long nmalloc(int alignment, int size) {
        pointer = (pointer - size) & -alignment;
        if (pointer < 0) {
            throw new OutOfMemoryError("test memory stack frame exhausted");
        }
        return base + pointer;
    }

    @Override
    public long ncalloc(int alignment, int count, int size) {
        long address = nmalloc(alignment, count * size);
        TestNativeMemory.UNSAFE.setMemory(address, Math.max((long) count * size, 1), (byte) 0);
        return address;
    }
}
