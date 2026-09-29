package com.bdmajora.impetus.engine.impl.render.mesh.util;

import com.bdmajora.impetus.engine.impl.render.mesh.gl.BindlessBuffer;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;

class UploadStreamTest {
    @BeforeEach
    void capable() {
        TestGl.meshCapable();
    }

    private static void fencesSignal(boolean signalled) {
        Mockito.doAnswer(inv -> {
            inv.<IntBuffer>getArgument(2).put(0, 1);
            return signalled ? 0x9119 : 0x9118;
        }).when(TestGl.gl()).glGetSynci(Mockito.anyLong(), Mockito.anyInt(), Mockito.any());
    }

    @Test
    void uploadsAreStagedCommittedAndReclaimedByFence() {
        UploadStream stream = new UploadStream(64);
        BindlessBuffer target = new BindlessBuffer(256);
        long first = stream.upload(target, 0, 16);
        long second = stream.upload(target, 16, 16);
        assertEquals(first + 16, second);
        TestGl.gl().memPutInt(first, 1);
        stream.commit();
        Mockito.verify(TestGl.gl()).glCopyNamedBufferSubData(Mockito.anyInt(), Mockito.eq(target.getId()), Mockito.eq(0L), Mockito.eq(0L), Mockito.eq(16L));
        stream.commit();
        fencesSignal(false);
        stream.endFrame();
        stream.upload(target, 32, 32);
        fencesSignal(true);
        stream.endFrame();
        stream.endFrame();
        assertThrows(IllegalArgumentException.class, () -> stream.upload(target, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> stream.upload(target, 250, 16));
        assertThrows(IllegalArgumentException.class, () -> stream.upload(target, -1, 16));
        long reused = stream.upload(target, 0, 64);
        assertTrue(reused != 0);
        stream.endFrame();
        stream.delete();
    }

    @Test
    void aFullRingStallsUntilEarlierFramesRetire() {
        UploadStream stream = new UploadStream(32);
        BindlessBuffer target = new BindlessBuffer(256);
        fencesSignal(true);
        stream.upload(target, 0, 32);
        stream.upload(target, 32, 32);
        Mockito.verify(TestGl.gl(), Mockito.atLeastOnce()).glFinish();
        // With the fence never signalling the ring cannot be reclaimed, so the next upload gives up after its retries
        fencesSignal(false);
        stream.endFrame();
        assertThrows(IllegalStateException.class, () -> stream.upload(target, 64, 32));
        fencesSignal(true);
        stream.delete();
    }
}
