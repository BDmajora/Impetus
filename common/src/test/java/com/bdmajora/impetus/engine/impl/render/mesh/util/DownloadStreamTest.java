package com.bdmajora.impetus.engine.impl.render.mesh.util;

import com.bdmajora.impetus.engine.impl.render.mesh.gl.BindlessBuffer;
import com.bdmajora.impetus.engine.impl.render.mesh.gl.MappedDownloadBuffer;
import com.bdmajora.testing.TestGl;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;

class DownloadStreamTest {
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
    void downloadsReachTheirCallbacksOnceTheirFrameRetires() {
        DownloadStream stream = new DownloadStream(64);
        BindlessBuffer source = new BindlessBuffer(128);
        LongArrayList delivered = new LongArrayList();

        assertTrue(stream.download(source, 16, 32, delivered::add));
        Mockito.verify(TestGl.gl()).glCopyNamedBufferSubData(Mockito.eq(source.getId()), Mockito.anyInt(), Mockito.eq(16L), Mockito.eq(0L), Mockito.eq(32L));
        // The ring is full until that frame retires
        assertFalse(stream.download(source, 0, 64, delivered::add));

        fencesSignal(false);
        stream.endFrame();
        assertTrue(delivered.isEmpty());

        fencesSignal(true);
        stream.endFrame();
        assertEquals(1, delivered.size());
        assertTrue(stream.download(source, 0, 64, delivered::add));

        // A frame still pending at teardown has its fence deleted with the stream
        fencesSignal(false);
        stream.endFrame();
        stream.delete();
        Mockito.verify(TestGl.gl(), Mockito.times(2)).glDeleteSync(Mockito.anyLong());
    }

    @Test
    void aMappingTheDriverRefusesFailsLoudly() {
        Mockito.doReturn(0L).when(TestGl.gl()).nglMapNamedBufferRange(Mockito.anyInt(), Mockito.anyLong(), Mockito.anyLong(), Mockito.anyInt());
        assertThrows(IllegalStateException.class, () -> new MappedDownloadBuffer(64));
    }

    @Test
    void theDownloadBufferUnmapsOnce() {
        MappedDownloadBuffer buffer = new MappedDownloadBuffer(64);
        assertEquals(64, buffer.getSize());
        assertNotEquals(0L, buffer.getClientAddress());
        buffer.delete();
        buffer.delete();
        Mockito.verify(TestGl.gl()).glUnmapNamedBuffer(buffer.getId());
    }
}
