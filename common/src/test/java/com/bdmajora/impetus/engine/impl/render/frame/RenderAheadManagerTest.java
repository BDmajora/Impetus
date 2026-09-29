package com.bdmajora.impetus.engine.impl.render.frame;

import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RenderAheadManagerTest {
    @Test
    void waitsOnTheOldestFenceOncePastTheLimit() {
        RenderAheadManager manager = new RenderAheadManager();
        manager.endFrame();
        manager.endFrame();
        manager.endFrame();
        manager.startFrame(2);
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glClientWaitSync(1L, 1, Long.MAX_VALUE);
        Mockito.verify(TestGl.gl()).glDeleteSync(1L);
        manager.startFrame(5);
        Mockito.verify(TestGl.gl(), Mockito.times(1)).glDeleteSync(Mockito.anyLong());
        Mockito.when(TestGl.gl().glFenceSync(Mockito.anyInt(), Mockito.anyInt())).thenReturn(0L);
        assertThrows(RuntimeException.class, manager::endFrame);
    }
}
