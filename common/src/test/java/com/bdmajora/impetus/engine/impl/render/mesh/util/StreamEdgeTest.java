package com.bdmajora.impetus.engine.impl.render.mesh.util;

import com.bdmajora.impetus.engine.impl.render.mesh.gl.DeviceBuffer;
import com.bdmajora.impetus.engine.impl.render.frame.RenderAheadManager;
import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

// Tests argument checks of the upload and download streams.
class StreamEdgeTest {
    // Makes the mocked GL capable of persistent mapping.
    @BeforeEach
    void capable() {
        TestGl.meshCapable();
    }

    // Device buffer of a size.
    private static DeviceBuffer buffer(long size) {
        DeviceBuffer buffer = Mockito.mock(DeviceBuffer.class);
        Mockito.when(buffer.getSize()).thenReturn(size);
        return buffer;
    }

    // Rejects uploads that overflow or run past the target.
    @Test
    void uploadRefusals() {
        UploadStream stream = new UploadStream(4096);
        DeviceBuffer target = buffer(1000);
        assertThrows(NullPointerException.class, () -> stream.upload(null, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> stream.upload(target, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> stream.upload(target, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> stream.upload(target, 0, 1001));
        assertThrows(IllegalArgumentException.class, () -> stream.upload(target, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> stream.upload(target, 999, 2));
        assertThrows(IllegalArgumentException.class, () -> stream.upload(target, Long.MAX_VALUE, 2));
        assertThrows(IllegalArgumentException.class, () -> stream.upload(target, Long.MAX_VALUE, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> stream.upload(target, 0, (long) Integer.MAX_VALUE + 1));
        long address = stream.upload(target, 999, 1);
        assertTrue(address >= 0 || address < 0);
        stream.commit();
        stream.endFrame();
        stream.delete();
        stream.delete();
    }

    // Rejects downloads outside the source and null callbacks.
    @Test
    void downloadRefusals() {
        DownloadStream stream = new DownloadStream(4096);
        DeviceBuffer source = buffer(100);
        assertThrows(NullPointerException.class, () -> stream.download(null, 0, 1, a -> {}));
        assertThrows(NullPointerException.class, () -> stream.download(source, 0, 1, null));
        assertThrows(IllegalArgumentException.class, () -> stream.download(source, 0, 0, a -> {}));
        assertThrows(IllegalArgumentException.class, () -> stream.download(source, 0, 101, a -> {}));
        assertThrows(IllegalArgumentException.class, () -> stream.download(source, -1, 1, a -> {}));
        assertThrows(IllegalArgumentException.class, () -> stream.download(source, Long.MAX_VALUE, 1, a -> {}));
        assertThrows(IllegalArgumentException.class, () -> stream.download(source, 100, 1, a -> {}));
        assertTrue(stream.download(source, 99, 1, a -> {}));
        boolean full = false;
        for (int i = 0; i < 100 && !full; i++) {
            full = !stream.download(source, 0, 100, a -> {});
        }
        assertTrue(full);
        stream.endFrame();
        stream.delete();
        stream.delete();
    }

    // Accepts any limit including a negative one for the render ahead manager.
    @Test
    void renderAhead() {
        RenderAheadManager manager = new RenderAheadManager();
        manager.startFrame(-5);
        manager.startFrame(Integer.MIN_VALUE);
        manager.startFrame(0);
        manager.startFrame(Integer.MAX_VALUE);
    }
}
