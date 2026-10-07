package com.bdmajora.impetus.engine.impl.common.util;

import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

class NativeBufferTest {
    @AfterEach
    void disableTracing() {
        NativeBuffer.ENABLE_MEMORY_TRACING = false;
    }

    @Test
    void allocatesCopiesAndFrees() {
        long before = NativeBuffer.getTotalAllocated();
        ByteBuffer src = ByteBuffer.allocateDirect(8).order(java.nio.ByteOrder.nativeOrder());
        src.putInt(0, 0x11223344).putInt(4, 0x55667788);
        NativeBuffer copy = NativeBuffer.copy(src);
        assertEquals(8, copy.getLength());
        assertEquals(before + 8, NativeBuffer.getTotalAllocated());
        assertEquals(0x11223344, copy.getDirectBuffer().getInt(0));
        copy.free();
        assertEquals(before, NativeBuffer.getTotalAllocated());
        assertThrows(IllegalStateException.class, copy::getDirectBuffer);
        assertThrows(IllegalStateException.class, copy::free);
    }

    @Test
    void reclaimFreesLeakedBuffersWithAndWithoutTracing() throws InterruptedException {
        NativeBuffer.ENABLE_MEMORY_TRACING = true;
        long before = NativeBuffer.getTotalAllocated();
        new NativeBuffer(16);
        NativeBuffer.ENABLE_MEMORY_TRACING = false;
        new NativeBuffer(32);
        NativeBuffer kept = new NativeBuffer(4);
        kept.free();
        // Phantom references are enqueued by the reference handler thread some time after the collection
        for (int i = 0; i < 100 && NativeBuffer.getTotalAllocated() > before; i++) {
            NativeBuffer.reclaim(true);
            Thread.sleep(20);
        }
        NativeBuffer.reclaim(false);
        // Other tests' leaked buffers may be swept up too, so only the 48 bytes from here are required to be gone
        assertTrue(NativeBuffer.getTotalAllocated() <= before);
    }

    @Test
    void retriesAllocationThenGivesUp() {
        try (MockedStatic<MemoryUtil> memory = Mockito.mockStatic(MemoryUtil.class, Mockito.CALLS_REAL_METHODS)) {
            memory.when(() -> MemoryUtil.nmemAlloc(Mockito.anyLong())).thenReturn(0L);
            assertThrows(OutOfMemoryError.class, () -> new NativeBuffer(8));
            memory.verify(() -> MemoryUtil.nmemAlloc(8), Mockito.times(3));
        }
    }
}
