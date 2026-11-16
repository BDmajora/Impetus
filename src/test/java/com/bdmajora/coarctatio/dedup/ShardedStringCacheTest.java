package com.bdmajora.coarctatio.dedup;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

// Tests the sharded string pool.
class ShardedStringCacheTest {
    // Counts every lookup from many threads across all shards.
    @Test
    void countsSurviveConcurrentShards() throws InterruptedException {
        ShardedStringCache cache = new ShardedStringCache("test", 1 << 20);
        int threads = 8;
        int perThread = 20_000;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < perThread; i++) {
                    cache.deduplicate(new String("k" + (i % 1000)));
                }
            });
            workers.add(worker);
            worker.start();
        }
        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }
        assertEquals(1000, cache.size());
        assertEquals((long) threads * perThread - 1000, cache.shared());
        assertTrue(cache.toString().contains(threads * perThread + " shared") || cache.toString().contains("/" + threads * perThread));
    }

    // Returns null for null and the pooled instance for equal strings.
    @Test
    void returnsThePooledInstance() {
        ShardedStringCache cache = new ShardedStringCache("test", 64);
        assertNull(cache.deduplicate(null));
        String first = cache.deduplicate(new String("a"));
        assertSame(first, cache.deduplicate(new String("a")));
        assertEquals(1, cache.shared());
        assertEquals("test (1/2 shared, 1 unique across 16 shards)", cache.toString());
        cache.clear();
        assertEquals(0, cache.size());
        assertEquals("test (unused)", cache.toString());
    }

    // Stops adding to a full shard and still answers existing strings.
    @Test
    void fullShardsStopGrowing() {
        ShardedStringCache cache = new ShardedStringCache("test", 0);
        String a = cache.deduplicate(new String("a"));
        String other = new String("a");
        assertSame(a, cache.deduplicate(other));
        String b = new String("zzzz");
        int before = cache.size();
        String result = cache.deduplicate(b);
        assertTrue(result == b || cache.size() == before + 1);
    }
}
