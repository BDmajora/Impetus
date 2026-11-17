package com.bdmajora.impetus.engine.impl.gl.sync;

import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;

// Tests polling and deletion of GL sync objects.
class GlFenceTest {
    // Polls signaled and unsignaled states and rejects use after delete.
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
        assertThrows(IllegalStateException.class, fence::isCompleted);
        fence.delete();
        Mockito.verify(TestGl.gl()).glDeleteSync(7);
        assertThrows(IllegalStateException.class, fence::isCompleted);
    }

    // Deletes the sync object once and keeps repeated deletes quiet.
    @Test
    void deleteIsIdempotent() {
        GlFence fence = new GlFence(Long.MAX_VALUE);
        fence.delete();
        fence.delete();
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glDeleteSync(Long.MAX_VALUE);
        assertThrows(IllegalStateException.class, fence::isCompleted);
    }

    // Checks a failed fence delete.
    @Test
    void failedDeleteKeepsFenceLive() {
        GlFence fence = new GlFence(9);
        Mockito.doThrow(new IllegalStateException("gl")).doNothing().when(TestGl.gl()).glDeleteSync(9L);
        assertThrows(IllegalStateException.class, fence::delete);
        fence.delete();
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glDeleteSync(9L);
        assertThrows(IllegalStateException.class, fence::isCompleted);
    }

    // Rejects a zero sync name and accepts every other long value.
    @Test
    void constructorRejectsOnlyZero() {
        assertThrows(IllegalArgumentException.class, () -> new GlFence(0L));
        for (long id : new long[]{1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, Integer.MAX_VALUE + 1L}) {
            GlFence fence = new GlFence(id);
            assertTrue(fence.isCompleted());
            fence.delete();
            Mockito.verify(TestGl.gl()).glDeleteSync(id);
        }
        Mockito.verify(TestGl.gl(), Mockito.never()).glDeleteSync(0L);
    }

    // Rejects every result length other than one and stays usable afterwards.
    @ParameterizedTest
    @ValueSource(ints = {0, 2, -1, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void wrongLengthIsRejected(int written) {
        GlFence fence = new GlFence(5);
        Mockito.when(TestGl.gl().glGetSynci(Mockito.eq(5L), Mockito.eq(0x9114), Mockito.any())).thenAnswer(inv -> {
            inv.<IntBuffer>getArgument(2).put(0, written);
            return 0x9119;
        });
        IllegalStateException error = assertThrows(IllegalStateException.class, fence::isCompleted);
        assertEquals("glGetSynci returned " + written + " values instead of one", error.getMessage());
        Mockito.reset(TestGl.gl());
        Mockito.when(TestGl.gl().glGetSynci(Mockito.eq(5L), Mockito.eq(0x9114), Mockito.any())).thenAnswer(inv -> {
            inv.<IntBuffer>getArgument(2).put(0, 1);
            return 0x9118;
        });
        assertFalse(fence.isCompleted());
    }

    // Reads a fresh zeroed length on every poll.
    @Test
    void repeatedPollsStartFromZeroLength() {
        GlFence fence = new GlFence(3);
        Mockito.when(TestGl.gl().glGetSynci(Mockito.eq(3L), Mockito.eq(0x9114), Mockito.any())).thenAnswer(inv -> {
            IntBuffer count = inv.getArgument(2);
            assertEquals(0, count.get(0));
            count.put(0, 1);
            return 0x9119;
        });
        for (int i = 0; i < 1000; i++) {
            assertTrue(fence.isCompleted());
        }
    }

    // Treats every status other than signaled as incomplete.
    @ParameterizedTest
    @ValueSource(ints = {0, 0x9118, 0x911A, -1, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void nonSignaledStatusesAreIncomplete(int status) {
        GlFence fence = new GlFence(4);
        Mockito.when(TestGl.gl().glGetSynci(Mockito.eq(4L), Mockito.eq(0x9114), Mockito.any())).thenAnswer(inv -> {
            inv.<IntBuffer>getArgument(2).put(0, 1);
            return status;
        });
        assertFalse(fence.isCompleted());
    }
}
