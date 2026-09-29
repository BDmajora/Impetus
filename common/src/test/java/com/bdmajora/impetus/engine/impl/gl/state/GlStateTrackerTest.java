package com.bdmajora.impetus.engine.impl.gl.state;

import com.bdmajora.impetus.engine.impl.gl.array.GlVertexArray;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlBufferTarget;
import com.bdmajora.impetus.engine.impl.gl.buffer.GlMutableBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlStateTrackerTest {
    @Test
    void redundantBindsAreSkippedUntilStateChanges() {
        GlStateTracker tracker = new GlStateTracker();
        GlMutableBuffer buffer = new GlMutableBuffer();
        GlVertexArray array = new GlVertexArray();
        assertTrue(tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, buffer));
        assertFalse(tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, buffer));
        assertTrue(tracker.makeVertexArrayActive(array));
        assertFalse(tracker.makeVertexArrayActive(array));
        assertTrue(tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, buffer));
        tracker.notifyBufferDeleted(buffer);
        assertTrue(tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, buffer));
        tracker.notifyVertexArrayDeleted(array);
        assertTrue(tracker.makeVertexArrayActive(array));
        assertTrue(tracker.makeVertexArrayActive(null));
        assertTrue(tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, buffer));
        assertTrue(tracker.makeBufferActive(GlBufferTarget.ARRAY_BUFFER, null));
        tracker.clear();
        assertTrue(tracker.makeVertexArrayActive(array));
        GlVertexArray other = new GlVertexArray();
        tracker.notifyVertexArrayDeleted(other);
        assertFalse(tracker.makeVertexArrayActive(array));
    }
}
