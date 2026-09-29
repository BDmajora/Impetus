package com.bdmajora.impetus.engine.impl.gl.profiling;

import com.bdmajora.testing.TestGl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class TimerQueryManagerTest {
    @Test
    void readsBackPairsOnceOldEnoughAndAvailable() {
        TimerQueryManager manager = new TimerQueryManager();
        assertThrows(IllegalStateException.class, manager::finishProfiling);
        manager.startProfiling();
        assertThrows(IllegalStateException.class, manager::startProfiling);
        manager.finishProfiling();
        manager.updateTime();
        assertEquals(0, manager.getLastTime());
        for (int i = 0; i < 2; i++) {
            manager.startProfiling();
            manager.finishProfiling();
        }
        Mockito.when(TestGl.gl().glGetQueryObjecti(Mockito.anyInt(), Mockito.anyInt())).thenReturn(0);
        manager.updateTime();
        assertEquals(0, manager.getLastTime());
        Mockito.when(TestGl.gl().glGetQueryObjecti(Mockito.anyInt(), Mockito.anyInt())).thenReturn(1);
        Mockito.when(TestGl.gl().glGetQueryObjectui64(Mockito.anyInt(), Mockito.anyInt())).thenReturn(100L, 350L);
        manager.updateTime();
        assertEquals(250, manager.getLastTime());
        for (int i = 0; i < 12; i++) {
            manager.startProfiling();
            manager.finishProfiling();
        }
        manager.updateTime();
        manager.startProfiling();
        manager.close();
        manager.startProfiling();
        manager.finishProfiling();
        manager.close();
        Mockito.verify(TestGl.gl(), Mockito.atLeast(2)).glQueryCounter(Mockito.anyInt(), Mockito.eq(0x8E28));
    }
}
