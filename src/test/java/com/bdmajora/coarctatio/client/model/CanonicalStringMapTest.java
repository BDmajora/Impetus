package com.bdmajora.coarctatio.client.model;

import com.bdmajora.coarctatio.dedup.DeduplicationCache;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CanonicalStringMapTest {
    @Test
    void everyKeyAndValueGoesThroughThePoolOnTheWayIn() {
        DeduplicationCache<String> pool = new DeduplicationCache<>("Test", 64);
        String texture = pool.deduplicate("blocks/stone");
        String name = pool.deduplicate("all");

        Map<String, String> source = new LinkedHashMap<>();
        source.put(new String("all"), new String("blocks/stone"));
        CanonicalStringMap map = new CanonicalStringMap(source, pool);

        assertEquals(1, map.size());
        assertSame(texture, map.get("all"));
        assertSame(name, map.keySet().iterator().next());

        // A mod appending later gets the same treatment
        map.put(new String("particle"), new String("blocks/stone"));
        assertSame(texture, map.get("particle"));
        map.putAll(Map.of(new String("side"), new String("blocks/stone")));
        assertSame(texture, map.get("side"));
    }
}
