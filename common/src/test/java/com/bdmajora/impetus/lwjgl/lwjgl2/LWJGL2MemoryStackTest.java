package com.bdmajora.impetus.lwjgl.lwjgl2;

import com.bdmajora.impetus.lwjgl.lwjgl2.memory.MemoryStack;
import com.bdmajora.impetus.lwjgl.lwjgl2.memory.MemoryUtilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LWJGL2MemoryStackTest {
    @Test
    void delegatesEveryCallToTheVendoredStack() {
        MemoryStack vendored = MemoryStack.create(1024);
        try (LWJGL2MemoryStack stack = new LWJGL2MemoryStack(vendored.push())) {
            assertEquals(vendored.getAddress(), stack.getAddress());
            assertEquals(1024, stack.getSize());
            assertEquals(1024, stack.getPointer());
            assertEquals(8, stack.malloc(8).capacity());
            assertEquals(1024 - 8, stack.getPointer());
            assertEquals(2, stack.mallocShort(2).capacity());
            assertEquals(2, stack.mallocInt(2).capacity());
            assertEquals(2, stack.mallocLong(2).capacity());
            assertEquals(2, stack.mallocFloat(2).capacity());
            assertEquals(0, stack.calloc(8).getLong(0));
            assertEquals(0, stack.callocShort(2).get(1));
            assertEquals(0, stack.callocInt(2).get(1));
            assertEquals(0L, stack.callocLong(2).get(1));
            assertEquals(0f, stack.callocFloat(2).get(1));
            assertEquals(0, stack.nmalloc(8) & 7);
            assertEquals(0, stack.nmalloc(32, 8) & 31);
            long zeroed = stack.ncalloc(8, 2, 8);
            assertEquals(0L, MemoryUtilities.memGetLong(zeroed + 8));
            long mark = stack.getPointer();
            stack.malloc(64);
            stack.setPointer(mark);
            assertEquals(mark, stack.getPointer());
            // The abstraction's convenience fillers run on top of the delegated primitives
            assertEquals(3, stack.ints(1, 2, 3).get(2));
            assertEquals(7, stack.ints(7).get(0));
            assertEquals(2.5f, stack.floats(2.5f).get(0));
        }
        assertEquals(0, vendored.getFrameIndex());
        assertEquals(1024, vendored.getPointer());
    }
}
