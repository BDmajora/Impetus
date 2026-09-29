package com.bdmajora.fulgor.async;

import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.util.math.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class LightQueueTest {
    @BeforeAll
    static void bootstrap() {
        Mc.bootstrap();
    }

    @Test
    void blockChangesAreBatchedPerChunk() {
        LightQueue queue = new LightQueue();
        assertTrue(queue.isEmpty());
        assertFalse(queue.hasWork());
        assertEquals(0, queue.size());
        queue.queueBlockChange(1, 64, 2);
        queue.queueBlockChange(3, 65, 4);
        queue.queueBlockChange(20, 64, 2);
        assertEquals(2, queue.size());
        assertTrue(queue.hasWork());
        assertTrue(queue.hasPendingWork(0, 0));
        assertTrue(queue.hasPendingLightWork(ChunkPos.asLong(0, 0)));

        ChunkTasks first = queue.removeFirstBlockChangeTask();
        assertEquals(ChunkPos.asLong(0, 0), first.chunkCoordinate);
        assertEquals(2, first.changedPositions.size());
        assertTrue(first.changedPositions.contains(1 | (2 << 4) | (64 << 8)));
        // A dequeued task is in flight until it completes, and the chunk still counts as busy
        assertTrue(queue.hasPendingWork(0, 0));
        assertTrue(queue.hasPendingLightWork(ChunkPos.asLong(0, 0)));
        assertNotNull(queue.getPendingWorkFuture(ChunkPos.asLong(0, 0)));
        queue.completeTask(first);
        assertTrue(first.onComplete.isDone());
        assertFalse(queue.hasPendingWork(0, 0));
        assertNotNull(queue.removeFirstBlockChangeTask());
        assertNull(queue.removeFirstBlockChangeTask());
        assertNull(queue.getPendingWorkFuture(ChunkPos.asLong(9, 9)));
    }

    @Test
    void sectionChangesAndLoadInitAreRecordedOnTheSameBatch() {
        LightQueue queue = new LightQueue();
        queue.queueSectionChange(0, 4, 0, true);
        queue.queueSectionChange(0, 5, 0, false);
        // Sections outside the world are ignored rather than queued
        queue.queueSectionChange(0, -1, 0, true);
        queue.queueSectionChange(0, 16, 0, true);
        Chunk chunk = mock(Chunk.class);
        queue.queueChunkLoadInit(0, 0, chunk, new Boolean[16]);
        ChunkTasks task = queue.removeFirstTask();
        assertEquals(Boolean.TRUE, task.changedSectionSet[4]);
        assertEquals(Boolean.FALSE, task.changedSectionSet[5]);
        assertSame(chunk, task.loadInitChunk);
        assertNotNull(task.loadInitEmptySections);
        assertNull(queue.removeFirstTask());
    }

    @Test
    void initialLightTasksCarryAGenerationAndRetryCount() {
        LightQueue queue = new LightQueue();
        Chunk chunk = mock(Chunk.class);
        assertFalse(queue.hasInitialLightTask());
        queue.queueChunkLight(0, 0, chunk, new Boolean[16], 5L);
        assertTrue(queue.hasInitialLightTask());
        // An older generation cannot displace a newer one
        queue.queueChunkLight(0, 0, chunk, new Boolean[16], 2L);
        // A requeue for the same generation counts another attempt
        assertTrue(queue.requeueChunkLight(0, 0, chunk, new Boolean[16], 5L, 1));
        assertTrue(queue.requeueChunkLight(0, 0, chunk, new Boolean[16], 7L, 0));
        ChunkTasks task = queue.removeFirstInitialLightTask();
        assertNotNull(task);
        assertEquals(7L, task.initialLightGeneration);
        assertEquals(1, task.relightAttempts);
        assertFalse(queue.hasInitialLightTask());
        assertNull(queue.removeFirstInitialLightTask());
        queue.completeTask(task);

        // An edge-check pass is refused while a newer initial pass is still queued for the chunk
        queue.queueChunkLight(1, 1, chunk, new Boolean[16], 9L);
        assertFalse(queue.queueInitialLightEdgeCheckAllSections(1, 1, true, 9L, 0));
        assertTrue(queue.queueInitialLightEdgeCheckAllSections(2, 2, true, 3L, 0));
        assertTrue(queue.queueInitialLightEdgeCheckAllSections(2, 2, false, 3L, 2));
        assertThrows(IllegalArgumentException.class,
                () -> queue.queueInitialLightEdgeCheckAllSections(2, 2, true, 0L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> queue.queueInitialLightEdgeCheckAllSections(2, 2, true, 1L, -1));
        queue.queueEdgeCheckAllSections(3, 3, true);
        queue.queueEdgeCheckAllSections(3, 3, false);

        ChunkTasks edges = null;
        for (ChunkTasks next = queue.removeFirstTask(); next != null; next = queue.removeFirstTask()) {
            if (next.chunkCoordinate == ChunkPos.asLong(2, 2)) {
                edges = next;
            }
        }
        assertNotNull(edges);
        assertEquals(18, edges.queuedEdgeChecksSky.size());
        assertEquals(18, edges.queuedEdgeChecksBlock.size());
        assertEquals(3L, edges.initialLightEdgeGeneration);
        assertEquals(2, edges.edgeCheckAttempts);
    }

    @Test
    void removingAChunkCompletesWhateverItHadPending() {
        LightQueue queue = new LightQueue();
        Chunk chunk = mock(Chunk.class);
        queue.queueChunkLight(4, 4, chunk, new Boolean[16], 1L);
        assertTrue(queue.hasInitialLightTask());
        ChunkTasks pending = Mixins.<it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<ChunkTasks>>get(queue, "tasksByChunk")
                .get(ChunkPos.asLong(4, 4));
        queue.removeChunk(4, 4);
        assertTrue(pending.onComplete.isDone());
        assertFalse(queue.hasInitialLightTask());
        assertFalse(queue.hasWork());
        // Removing a chunk with nothing queued is a no-op
        queue.removeChunk(4, 4);
    }

    @Test
    void theWorkSignalBlocksUntilSomethingIsQueued() throws Exception {
        LightQueue queue = new LightQueue();
        queue.wakeUp();
        queue.waitForWork();
        queue.clearWorkSignal();
        Thread waiter = new Thread(() -> {
            try {
                queue.waitForWork();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        waiter.start();
        Thread.sleep(20);
        assertTrue(waiter.isAlive());
        queue.queueBlockChange(0, 64, 0);
        waiter.join(2000);
        assertFalse(waiter.isAlive());
    }
}
