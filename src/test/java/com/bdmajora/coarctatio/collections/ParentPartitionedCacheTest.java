package com.bdmajora.coarctatio.collections;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

// Tests the cache that keeps one partition per parent.
class ParentPartitionedCacheTest {
    // Returns the interned prefix of a key before its colon or null.
    private static String parentOf(String key) {
        int colon = key.indexOf(':');
        return colon < 0 ? null : key.substring(0, colon).intern();
    }

    // Returns a cache with room for two parents of two entries each.
    private static ParentPartitionedCache<String, String, Integer> cache() {
        return new ParentPartitionedCache<>(ParentPartitionedCacheTest::parentOf, 2, 2, Duration.ofMinutes(1));
    }

    // Rejects a null function or expiry and negative limits.
    @Test
    void constructorRefusals() {
        assertThrows(NullPointerException.class, () -> new ParentPartitionedCache<String, String, Integer>(null, 1, 1, Duration.ZERO));
        assertThrows(NullPointerException.class, () -> new ParentPartitionedCache<String, String, Integer>(k -> null, 1, 1, null));
        assertThrows(IllegalArgumentException.class, () -> new ParentPartitionedCache<String, String, Integer>(k -> null, -1, 1, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ParentPartitionedCache<String, String, Integer>(k -> null, 1, -1, Duration.ZERO));
    }

    // Loads once per key and answers later calls from the cache.
    @Test
    void getLoadsOnce() throws ExecutionException {
        ParentPartitionedCache<String, String, Integer> cache = cache();
        assertEquals(1, cache.get("a:1", () -> 1));
        assertEquals(1, cache.get("a:1", () -> {
            throw new AssertionError("loaded twice");
        }));
        assertEquals(5, cache.get("orphan", () -> 5));
        assertEquals(2, cache.size());
        assertEquals(1, cache.parentCount());
        assertThrows(ExecutionException.class, () -> cache.get("b:1", () -> {
            throw new Exception("load");
        }));
        assertThrows(NullPointerException.class, () -> cache.get(null, () -> 1));
        assertThrows(NullPointerException.class, () -> cache.get("a:2", null));
    }

    // Stores and reads and removes values without creating a partition on a read.
    @Test
    void putReadAndInvalidate() {
        ParentPartitionedCache<String, String, Integer> cache = cache();
        assertNull(cache.getIfPresent("x:1"));
        assertEquals(0, cache.parentCount());
        cache.put("x:1", 1);
        cache.put("x:2", 2);
        cache.put("orphan", 3);
        assertEquals(2, cache.getIfPresent("x:2"));
        assertEquals(Map.of("x:1", 1, "orphan", 3), cache.getAllPresent(List.of("x:1", "orphan", "missing", "x:1")));
        assertEquals(Map.of("x:1", 1, "x:2", 2, "orphan", 3), cache.asMap());
        cache.invalidate("x:1");
        cache.invalidate("y:1");
        assertNull(cache.getIfPresent("x:1"));
        cache.putAll(Map.of("z:1", 4));
        assertEquals(4, cache.getIfPresent("z:1"));
        cache.invalidateAll(List.of("z:1", "orphan"));
        assertEquals(1, cache.size());
        cache.invalidateAll();
        assertEquals(0, cache.size());
        cache.cleanUp();
        assertThrows(NullPointerException.class, () -> cache.put("a:1", null));
        assertThrows(NullPointerException.class, () -> cache.put(null, 1));
        assertThrows(NullPointerException.class, () -> cache.getIfPresent(null));
        assertThrows(NullPointerException.class, () -> cache.invalidate(null));
    }

    // Keeps each partition within its entry limit and counts hits and misses across partitions.
    @Test
    void limitsAndStatistics() throws ExecutionException {
        ParentPartitionedCache<String, String, Integer> cache = cache();
        for (int i = 0; i < 10; i++) {
            cache.put("p:" + i, i);
        }
        cache.cleanUp();
        assertTrue(cache.size() <= 2);
        cache.get("q:1", () -> 1);
        cache.get("q:1", () -> 1);
        assertEquals(1, cache.stats().hitCount());
        assertEquals(1, cache.stats().missCount());
    }
}
