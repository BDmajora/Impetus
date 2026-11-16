package com.bdmajora.fulgor.lighting;

import com.bdmajora.fulgor.collections.DeduplicatedLongQueue;
import com.bdmajora.testing.Mc;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LightUtilEdgeTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @Test
    void heightsOutsideTheWorldReadAsAir() {
        Chunk chunk = mock(Chunk.class);
        when(chunk.getBlockStorageArray()).thenReturn(new ExtendedBlockStorage[16]);
        assertSame(Blocks.AIR.getDefaultState(), LightUtil.posToState(new BlockPos(0, -1, 0), chunk));
        assertSame(Blocks.AIR.getDefaultState(), LightUtil.posToState(new BlockPos(0, 256, 0), chunk));
        assertSame(Blocks.AIR.getDefaultState(), LightUtil.posToState(new BlockPos(0, 4000, 0), chunk));
        assertSame(Blocks.AIR.getDefaultState(), LightUtil.posToState(new BlockPos(0, 0, 0), chunk));
        assertSame(Blocks.AIR.getDefaultState(), LightUtil.posToState(new BlockPos(0, 255, 0), chunk));
    }

    @Test
    void emptyDeduplicatedQueueRefusesToDequeue() {
        DeduplicatedLongQueue queue = new DeduplicatedLongQueue(new DeduplicatedLongQueue.Pool(), 4, true);
        assertTrue(queue.isEmpty());
        assertThrows(NoSuchElementException.class, queue::dequeue);
        assertTrue(queue.enqueue(5L));
        assertFalse(queue.enqueue(5L));
        assertEquals(5L, queue.dequeue());
        assertTrue(queue.isEmpty());
        assertThrows(NoSuchElementException.class, queue::dequeue);
        queue.resetDeduplication();
        for (long i = 0; i < 3000; i++) {
            queue.enqueue(i);
        }
        assertEquals(3000, queue.size());
        for (long i = 0; i < 3000; i++) {
            assertEquals(i, queue.dequeue());
        }
        assertTrue(queue.isEmpty());
        assertFalse(queue.enqueue(5L));
        queue.resetDeduplication();
        assertTrue(queue.enqueue(5L));
    }
}
