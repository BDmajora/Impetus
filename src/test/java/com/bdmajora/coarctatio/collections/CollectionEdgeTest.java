package com.bdmajora.coarctatio.collections;

import com.bdmajora.coarctatio.dedup.DeduplicationCache;
import com.bdmajora.coarctatio.client.model.CanonicalStringMap;
import com.bdmajora.coarctatio.nbt.NbtPrimitivePool;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class CollectionEdgeTest {
    @Test
    void arrayMapKeepsOrderAndRefusesEdits() {
        Map<String, Integer> source = new LinkedHashMap<>();
        source.put("b", 1);
        source.put(null, 2);
        source.put("a", null);
        ArrayBackedLinkedMap<String, Integer> map = new ArrayBackedLinkedMap<>(source);
        assertEquals(3, map.size());
        assertEquals(source, map);
        assertEquals(Arrays.asList("b", null, "a"), new ArrayList<>(map.keySet()));
        assertEquals(2, map.get(null));
        assertTrue(map.containsKey("a"));
        assertNull(map.get("a"));
        assertNull(map.get("zz"));
        assertFalse(map.containsKey("zz"));
        assertThrows(UnsupportedOperationException.class, () -> map.put("x", 1));
        assertThrows(UnsupportedOperationException.class, () -> map.remove("b"));
        assertThrows(UnsupportedOperationException.class, map::clear);
        Iterator<Map.Entry<String, Integer>> it = map.entrySet().iterator();
        it.next();
        assertThrows(UnsupportedOperationException.class, it::remove);
        it.next();
        it.next();
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void arrayMapOfNothingIsEmpty() {
        ArrayBackedLinkedMap<String, String> map = new ArrayBackedLinkedMap<>(new HashMap<>());
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
        assertFalse(map.entrySet().iterator().hasNext());
        assertThrows(NullPointerException.class, () -> new ArrayBackedLinkedMap<>(null));
    }

    @Test
    void fixedListBehavesLikeAnImmutableList() {
        List<String> source = new ArrayList<>(Arrays.asList("a", null, "b", "a"));
        FixedArrayList<String> list = new FixedArrayList<>(source);
        source.clear();
        assertEquals(4, list.size());
        assertEquals(Arrays.asList("a", null, "b", "a"), list);
        assertEquals(list, Arrays.asList("a", null, "b", "a"));
        assertEquals(Arrays.asList("a", null, "b", "a").hashCode(), list.hashCode());
        assertEquals(0, list.indexOf("a"));
        assertEquals(3, list.lastIndexOf("a"));
        assertEquals(1, list.indexOf(null));
        assertEquals(-1, list.indexOf("zz"));
        assertTrue(list.contains(null));
        assertTrue(list.containsAll(Arrays.asList("a", "b")));
        assertFalse(list.containsAll(Arrays.asList("a", "q")));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(4));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
        assertThrows(UnsupportedOperationException.class, () -> list.add("x"));
        assertThrows(UnsupportedOperationException.class, () -> list.set(0, "x"));
        assertThrows(UnsupportedOperationException.class, () -> list.subList(0, 2).add("x"));
        assertThrows(UnsupportedOperationException.class, () -> list.sort(java.util.Comparator.nullsFirst(java.util.Comparator.<String>naturalOrder())));
        assertThrows(UnsupportedOperationException.class, () -> list.removeIf(x -> true));
        assertEquals(Arrays.asList("a", null), list.subList(0, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> list.subList(2, 9));
        assertNotEquals(list, Arrays.asList("a", null, "b"));
        assertNotEquals(list, "text");
        assertEquals("[a, null, b, a]", list.toString());
    }

    @Test
    void fixedListCopiesIntoArraysOfAnySize() {
        FixedArrayList<String> list = new FixedArrayList<>(Arrays.asList("a", "b"));
        Object[] copy = list.toArray();
        copy[0] = "changed";
        assertEquals("a", list.get(0));
        String[] exact = list.toArray(new String[2]);
        assertArrayEquals(new String[] {"a", "b"}, exact);
        String[] big = {"x", "x", "x"};
        assertSame(big, list.toArray(big));
        assertNull(big[2]);
        assertEquals(2, list.toArray(new String[0]).length);
        assertEquals(0, new FixedArrayList<String>(Arrays.asList()).toArray(new String[0]).length);
        assertThrows(NullPointerException.class, () -> list.toArray((String[]) null));
        assertThrows(NullPointerException.class, () -> new FixedArrayList<String>((List<String>) null));
        assertThrows(ArrayStoreException.class, () -> list.toArray(new Integer[2]));
    }

    @Test
    void helperKeepsNullAndAlreadyFixedLists() {
        assertNull(CollectionHelper.fixed(null));
        FixedArrayList<String> fixed = new FixedArrayList<>(Arrays.asList("a"));
        assertSame(fixed, CollectionHelper.fixed(fixed));
        List<String> copy = CollectionHelper.fixed(new ArrayList<>(Arrays.asList("a")));
        assertInstanceOf(FixedArrayList.class, copy);
    }

    @Test
    void poolNeverGrowsPastItsLimit() {
        DeduplicationCache<String> pool = new DeduplicationCache<>("Limit", 3);
        for (int i = 0; i < 100; i++) {
            pool.deduplicate("v" + i);
        }
        assertEquals(3, pool.size());
        assertTrue(pool.toString().contains("saturated"));
        String early = new String("v0");
        assertEquals("v0", pool.deduplicate(early));
        assertNotSame(early, pool.deduplicate(early));
        assertEquals(3, pool.size());
    }

    @Test
    void zeroLimitPoolStoresNothing() {
        DeduplicationCache<String> pool = new DeduplicationCache<>("Zero", 0);
        String value = new String("a");
        assertSame(value, pool.deduplicate(value));
        assertEquals(0, pool.size());
    }

    @Test
    void canonicalMapSharesEntriesWithThePool() {
        DeduplicationCache<String> pool = new DeduplicationCache<>("Strings", 16);
        String key = new String("facing");
        Map<String, String> source = new HashMap<>();
        source.put(new String("facing"), new String("north"));
        CanonicalStringMap first = new CanonicalStringMap(source, pool);
        CanonicalStringMap second = new CanonicalStringMap(source, pool);
        assertSame(first.keySet().iterator().next(), second.keySet().iterator().next());
        assertSame(first.values().iterator().next(), second.values().iterator().next());
        first.put(key, null);
        assertNull(first.get("facing"));
        assertEquals(0, new CanonicalStringMap(new HashMap<>(), pool).size());
        assertThrows(NullPointerException.class, () -> new CanonicalStringMap(null, pool));
    }

    @Test
    void primitivePoolHandlesBoundaryValues() {
        assertSame(NbtPrimitivePool.of((byte) -128), NbtPrimitivePool.of((byte) -128));
        assertSame(NbtPrimitivePool.of((byte) 127), NbtPrimitivePool.of((byte) 127));
        assertSame(NbtPrimitivePool.of(-128), NbtPrimitivePool.of(-128));
        assertSame(NbtPrimitivePool.of(1023), NbtPrimitivePool.of(1023));
        assertNotSame(NbtPrimitivePool.of(1024), NbtPrimitivePool.of(1024));
        assertNotSame(NbtPrimitivePool.of(-129), NbtPrimitivePool.of(-129));
        assertSame(NbtPrimitivePool.of(1023L), NbtPrimitivePool.of(1023L));
        assertNotSame(NbtPrimitivePool.of(Long.MAX_VALUE), NbtPrimitivePool.of(Long.MAX_VALUE));
        assertEquals(Long.MIN_VALUE, NbtPrimitivePool.of(Long.MIN_VALUE).getLong());
        assertNotSame(NbtPrimitivePool.of(4294967296L), NbtPrimitivePool.of(4294967296L));
        assertEquals(4294967296L, NbtPrimitivePool.of(4294967296L).getLong());
        assertSame(NbtPrimitivePool.of((short) 5), NbtPrimitivePool.of((short) 5));
        assertEquals(Short.MIN_VALUE, NbtPrimitivePool.of(Short.MIN_VALUE).getShort());
        assertThrows(NullPointerException.class, () -> NbtPrimitivePool.canonical(null));
    }
}
