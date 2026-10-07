package com.bdmajora.impetus.engine.impl.gl.sync;

import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;

class GlFenceTest {
    @Test
    void pollsWaitsAndRefusesUseAfterDelete() {
        GlFence fence = new GlFence(7);
        Mockito.when(TestGl.gl().glGetSynci(Mockito.eq(7L), Mockito.eq(0x9114), Mockito.any())).thenAnswer(inv -> {
            inv.<IntBuffer>getArgument(2).put(0, 1);
            return 0x9119;
        });
        assertTrue(fence.isCompleted());
        Mockito.when(TestGl.gl().glGetSynci(Mockito.eq(7L), Mockito.eq(0x9114), Mockito.any())).thenAnswer(inv -> {
            inv.<IntBuffer>getArgument(2).put(0, 1);
            return 0x9118;
        });
        assertFalse(fence.isCompleted());
        Mockito.when(TestGl.gl().glGetSynci(Mockito.eq(7L), Mockito.eq(0x9114), Mockito.any())).thenReturn(0x9119);
        assertThrows(RuntimeException.class, fence::isCompleted);
        // The CPU wait retries through timeouts until the GPU passes the fence
        Mockito.when(TestGl.gl().glClientWaitSync(7, 1, 1_000_000L)).thenReturn(0x911B, 0x911C);
        fence.await();
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glClientWaitSync(7, 1, 1_000_000L);
        fence.delete();
        Mockito.verify(TestGl.gl()).glDeleteSync(7);
        assertThrows(IllegalStateException.class, fence::isCompleted);
        assertThrows(IllegalStateException.class, fence::await);
    }
}
