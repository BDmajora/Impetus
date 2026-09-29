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
        fence.sync();
        Mockito.verify(TestGl.gl()).glWaitSync(7, 1, Long.MAX_VALUE);
        fence.sync(5);
        Mockito.verify(TestGl.gl()).glWaitSync(7, 1, 5);
        fence.delete();
        Mockito.verify(TestGl.gl()).glDeleteSync(7);
        assertThrows(IllegalStateException.class, fence::isCompleted);
        assertThrows(IllegalStateException.class, fence::sync);
        assertThrows(IllegalStateException.class, () -> fence.sync(1));
    }
}
