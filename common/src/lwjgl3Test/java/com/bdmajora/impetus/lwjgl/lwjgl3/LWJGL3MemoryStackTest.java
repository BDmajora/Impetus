package com.bdmajora.impetus.lwjgl.lwjgl3;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

class LWJGL3MemoryStackTest {
    @Test
    void delegatesToLwjglsThreadStack() {
        long outer = MemoryStack.stackGet().getPointer();
        try (LWJGL3MemoryStack stack = new LWJGL3MemoryStack(MemoryStack.stackPush())) {
            assertEquals(MemoryStack.stackGet().getAddress(), stack.getAddress());
            assertEquals(MemoryStack.stackGet().getSize(), stack.getSize());
            long frame = stack.getPointer();

            // The stack grows down, so every allocation lowers the pointer
            assertEquals(4, stack.malloc(4).capacity());
            assertTrue(stack.getPointer() < frame);
            assertEquals(2, stack.mallocShort(2).capacity());
            assertEquals(2, stack.mallocInt(2).capacity());
            assertEquals(2, stack.mallocLong(2).capacity());
            assertEquals(2, stack.mallocFloat(2).capacity());

            assertEquals(0, stack.calloc(4).get(3));
            assertEquals(0, stack.callocShort(2).get(1));
            assertEquals(0, stack.callocInt(2).get(1));
            assertEquals(0L, stack.callocLong(2).get(1));
            assertEquals(0f, stack.callocFloat(2).get(1));

            assertNotEquals(0L, stack.nmalloc(8));
            assertEquals(0L, stack.nmalloc(64, 8) % 64);
            long zeroed = stack.ncalloc(8, 2, 4);
            assertEquals(0L, zeroed % 8);
            assertEquals(0, MemoryUtil.memGetInt(zeroed + 4));

            stack.setPointer(frame);
            assertEquals(frame, stack.getPointer());
        }
        // Closing pops the frame pushed above
        assertEquals(outer, MemoryStack.stackGet().getPointer());
    }
}
