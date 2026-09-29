package com.google.common.collect;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CoarctatioPropertyMapTest {
    private static final Object[] KEYS = {"north", "east"};

    private static CoarctatioPropertyMap<String, String> map() {
        return new CoarctatioPropertyMap<>(KEYS, new Object[] {"true", "false"});
    }

    @Test
    void itReadsLikeTheImmutableMapItReplaces() {
        CoarctatioPropertyMap<String, String> map = map();
        assertEquals(2, map.size());
        assertFalse(map.isEmpty());
        assertEquals("true", map.get("north"));
        assertEquals("false", map.get(new String("east")));
        assertNull(map.get("south"));
        // ImmutableMap forbids null keys, so a null lookup is a miss rather than a scan
        assertNull(map.get(null));
        assertTrue(map.containsKey("north"));
        assertFalse(map.containsKey("south"));
        assertFalse(map.containsKey(null));
        assertTrue(map.containsValue("false"));
        assertFalse(map.containsValue("maybe"));
        assertFalse(map.containsValue(null));
        assertTrue(new CoarctatioPropertyMap<>(new Object[0], new Object[0]).isEmpty());
        // Both arrays are exactly sized, so Guava has no reason to copy it
        assertFalse(map.isPartialView());
    }

    @Test
    void theEntrySetIsOnlyBuiltWhenSomethingIteratesTheMap() {
        CoarctatioPropertyMap<String, String> map = map();
        ImmutableSet<Map.Entry<String, String>> entries = map.createEntrySet();
        assertEquals(2, entries.size());
        assertTrue(entries.contains(Maps.immutableEntry("north", "true")));
        // Guava caches it, so iterating twice costs one entry set
        assertEquals(entries, map.entrySet());
        assertEquals(2, map.keySet().size());
        assertEquals(2, map.values().size());
    }

    @Test
    void hashingAndComparingHappenOnTheArraysWithoutMaterialisingEntries() {
        CoarctatioPropertyMap<String, String> map = map();
        Map<String, String> equivalent = new LinkedHashMap<>();
        equivalent.put("north", "true");
        equivalent.put("east", "false");

        assertEquals(equivalent.hashCode(), map.hashCode());
        assertEquals(map, map);
        assertEquals(map, equivalent);
        assertEquals(map, new CoarctatioPropertyMap<>(KEYS, new Object[] {"true", "false"}));
        assertNotEquals(map, "not a map");

        // A different size, a different value and a missing key are all unequal
        assertNotEquals(map, new HashMap<>());
        Map<String, String> other = new LinkedHashMap<>(equivalent);
        other.put("east", "true");
        assertNotEquals(map, other);
        Map<String, String> renamed = new LinkedHashMap<>();
        renamed.put("north", "true");
        renamed.put("west", "false");
        assertNotEquals(map, renamed);

        // A stored null is told apart from an absent key through containsKey
        CoarctatioPropertyMap<String, String> withNull =
                new CoarctatioPropertyMap<>(new Object[] {"north"}, new Object[] {null});
        Map<String, String> nulled = new HashMap<>();
        nulled.put("north", null);
        assertEquals(withNull, nulled);
        assertNotEquals(withNull, new HashMap<>(java.util.Collections.singletonMap("north", "true")));
        assertNotEquals(withNull, new HashMap<>(java.util.Collections.singletonMap("south", null)));
        assertEquals(nulled.hashCode(), withNull.hashCode());
    }

    @Test
    void printingAStateDoesNotForceAnEntrySetIntoExistence() {
        assertEquals("{north=true, east=false}", map().toString());
        assertEquals("{}", new CoarctatioPropertyMap<>(new Object[0], new Object[0]).toString());
    }
}
