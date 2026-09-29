package com.bdmajora.coarctatio.collections;

import com.bdmajora.testing.Mixins;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class CollectionsTest {
    private static FixedArrayList<String> list() {
        return new FixedArrayList<>(new String[] {"a", "b", "c", "b"});
    }

    @Test
    void aFixedListReadsLikeTheArrayItWrapsAndIsBuiltFromEitherOne() {
        FixedArrayList<String> list = list();
        assertEquals(4, list.size());
        assertFalse(list.isEmpty());
        assertTrue(new FixedArrayList<>(new String[0]).isEmpty());
        assertEquals("a", list.get(0));
        assertTrue(list.contains("c"));
        assertFalse(list.contains("z"));
        assertTrue(list.containsAll(Arrays.asList("a", "b")));
        assertFalse(list.containsAll(Arrays.asList("a", "z")));
        assertEquals(1, list.indexOf("b"));
        assertEquals(3, list.lastIndexOf("b"));
        assertEquals(-1, list.indexOf("z"));
        assertEquals("[a, b, c, b]", list.toString());

        // The List overload copies out of the source and keeps its order
        FixedArrayList<String> copied = new FixedArrayList<>(new ArrayList<>(Arrays.asList("x", "y")));
        assertEquals(Arrays.asList("x", "y"), copied);

        Iterator<String> it = list.iterator();
        assertTrue(it.hasNext());
        assertEquals("a", it.next());
    }

    @Test
    void theArrayHandedOutIsNeverTheBackingOne() {
        FixedArrayList<String> list = list();
        Object[] copy = list.toArray();
        assertArrayEquals(new Object[] {"a", "b", "c", "b"}, copy);
        copy[0] = "mutated";
        assertEquals("a", list.get(0));

        // A destination that is too small is replaced by one of the caller's own component type
        String[] small = list.toArray(new String[1]);
        assertArrayEquals(new String[] {"a", "b", "c", "b"}, small);
        // An exactly sized one is filled as is, a longer one is null-terminated after the last element
        String[] exact = new String[4];
        assertSame(exact, list.toArray(exact));
        String[] roomy = list.toArray(new String[6]);
        assertEquals("b", roomy[3]);
        assertNull(roomy[4]);
    }

    @Test
    void everyMutatorThrowsSoABakeTimeEditFailsLoudly() {
        FixedArrayList<String> list = list();
        assertThrows(UnsupportedOperationException.class, () -> list.add("d"));
        assertThrows(UnsupportedOperationException.class, () -> list.add(0, "d"));
        assertThrows(UnsupportedOperationException.class, () -> list.remove("a"));
        assertThrows(UnsupportedOperationException.class, () -> list.remove(0));
        assertThrows(UnsupportedOperationException.class, () -> list.set(0, "d"));
        assertThrows(UnsupportedOperationException.class, () -> list.addAll(Arrays.asList("d")));
        assertThrows(UnsupportedOperationException.class, () -> list.addAll(0, Arrays.asList("d")));
        assertThrows(UnsupportedOperationException.class, () -> list.removeAll(Arrays.asList("a")));
        assertThrows(UnsupportedOperationException.class, () -> list.retainAll(Arrays.asList("a")));
        assertThrows(UnsupportedOperationException.class, list::clear);
    }

    @Test
    void theBorrowedViewsAreReadOnlyAndTheListCompares() {
        FixedArrayList<String> list = list();
        ListIterator<String> plain = list.listIterator();
        assertEquals("a", plain.next());
        assertThrows(UnsupportedOperationException.class, () -> plain.set("z"));
        assertEquals("b", list.listIterator(1).next());
        List<String> sub = list.subList(1, 3);
        assertEquals(Arrays.asList("b", "c"), sub);
        assertThrows(UnsupportedOperationException.class, () -> sub.set(0, "z"));

        // Equality and hashing follow the List contract, so any implementation compares equal
        assertEquals(list, list);
        assertEquals(list, new LinkedList<>(Arrays.asList("a", "b", "c", "b")));
        assertEquals(new ArrayList<>(Arrays.asList("a", "b", "c", "b")).hashCode(), list.hashCode());
        assertNotEquals(list, Arrays.asList("a", "b", "c"));
        assertNotEquals(list, Arrays.asList("a", "b", "c", "z"));
        assertNotEquals(list, "not a list");
        FixedArrayList<String> withNull = new FixedArrayList<>(new String[] {null});
        assertEquals(java.util.Collections.singletonList(null), withNull);
        assertEquals(java.util.Collections.singletonList(null).hashCode(), withNull.hashCode());
    }

    @Test
    void compactingALeavesNullAndAlreadyFixedListsAlone() {
        assertNull(CollectionHelper.fixed(null));
        FixedArrayList<String> fixed = list();
        assertSame(fixed, CollectionHelper.fixed(fixed));
        List<String> compacted = CollectionHelper.fixed(new ArrayList<>(Arrays.asList("a")));
        assertInstanceOf(FixedArrayList.class, compacted);
        assertEquals(Arrays.asList("a"), compacted);
        assertNotNull(Mixins.construct(CollectionHelper.class));
    }

    @Test
    void theLinkedMapKeepsInsertionOrderAndLooksUpByScan() {
        Map<String, Integer> src = new LinkedHashMap<>();
        src.put("one", 1);
        src.put("two", 2);
        src.put(null, 0);
        ArrayBackedLinkedMap<String, Integer> map = new ArrayBackedLinkedMap<>(src);
        assertEquals(3, map.size());
        assertFalse(map.isEmpty());
        assertTrue(new ArrayBackedLinkedMap<>(new LinkedHashMap<String, Integer>()).isEmpty());
        assertTrue(map.containsKey("two"));
        // A null key is looked up like any other, and a miss is indistinguishable from a stored null
        assertTrue(map.containsKey(null));
        assertFalse(map.containsKey("three"));
        assertEquals(2, map.get("two"));
        assertNull(map.get("three"));
        assertEquals(Arrays.asList("one", "two", null), new ArrayList<>(map.keySet()));
        assertEquals(Arrays.asList(1, 2, 0), new ArrayList<>(map.values()));

        Iterator<Map.Entry<String, Integer>> it = map.entrySet().iterator();
        assertEquals(3, map.entrySet().size());
        assertEquals("one", it.next().getKey());
        it.next();
        it.next();
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);

        assertThrows(UnsupportedOperationException.class, () -> map.put("x", 9));
        assertThrows(UnsupportedOperationException.class, () -> map.remove("one"));
        assertThrows(UnsupportedOperationException.class, map::clear);
    }
}
