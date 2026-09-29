package com.bdmajora.fulgor.collections;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeduplicatedLongQueueTest {
    @Test
    void theQueueCollapsesRepeatsUntilItIsReset() {
        DeduplicatedLongQueue.Pool pool = new DeduplicatedLongQueue.Pool();
        DeduplicatedLongQueue queue = new DeduplicatedLongQueue(pool, 16, true);
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
        assertTrue(queue.enqueue(5L));
        assertFalse(queue.enqueue(5L));
        assertTrue(queue.enqueue(6L));
        assertEquals(2, queue.size());
        assertFalse(queue.isEmpty());
        assertEquals(5L, queue.dequeue());
        assertEquals(6L, queue.dequeue());
        assertTrue(queue.isEmpty());
        // Still deduplicated after being drained, since the cycle has not ended
        assertFalse(queue.enqueue(5L));
        queue.resetDeduplication();
        assertTrue(queue.enqueue(5L));
        assertEquals(5L, queue.dequeue());
    }

    @Test
    void segmentsAreChainedAndReturnedToThePool() {
        DeduplicatedLongQueue.Pool pool = new DeduplicatedLongQueue.Pool();
        DeduplicatedLongQueue queue = new DeduplicatedLongQueue(pool, 16, false);
        // Two full segments plus one entry, so the queue chains and then releases as it drains
        int count = (1 << 10) * 2 + 1;
        for (int i = 0; i < count; i++) {
            assertTrue(queue.enqueue(i));
        }
        assertEquals(count, queue.size());
        for (int i = 0; i < count; i++) {
            assertEquals(i, queue.dequeue());
        }
        assertTrue(queue.isEmpty());
        // The freed segments are handed back out rather than reallocated
        queue.enqueue(1L);
        assertEquals(1L, queue.dequeue());
        // Without deduplication the same value may be queued repeatedly, and resetting is a no-op
        queue.resetDeduplication();
        assertTrue(queue.enqueue(7L));
        assertTrue(queue.enqueue(7L));
        assertEquals(2, queue.size());
        queue.dequeue();
        queue.dequeue();
    }

    @Test
    void anOversizedDeduplicationSetIsReplacedRatherThanCleared() {
        DeduplicatedLongQueue queue = new DeduplicatedLongQueue(new DeduplicatedLongQueue.Pool(), 16, true);
        for (int i = 0; i < (1 << 15) + 1; i++) {
            queue.enqueue(i);
        }
        queue.resetDeduplication();
        assertTrue(queue.enqueue(0L));
        while (!queue.isEmpty()) {
            queue.dequeue();
        }
        assertEquals(0, queue.size());
    }

    @Test
    void thePoolStopsRetainingSegmentsPastItsLimit() {
        DeduplicatedLongQueue.Pool pool = new DeduplicatedLongQueue.Pool();
        DeduplicatedLongQueue queue = new DeduplicatedLongQueue(pool, 16, false);
        // More segments than the pool keeps, so the last releases are dropped instead of cached
        int count = (1 << 10) * ((1 << 10) + 2);
        for (int i = 0; i < count; i++) {
            queue.enqueue(i);
        }
        while (!queue.isEmpty()) {
            queue.dequeue();
        }
        assertEquals(0, queue.size());
    }
}
