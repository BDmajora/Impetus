package com.bdmajora.impetus.engine.impl.util;

import com.bdmajora.impetus.engine.impl.util.collections.quadtree.Rect2i;
import com.bdmajora.impetus.engine.impl.util.iterator.ByteArrayIterator;
import com.bdmajora.impetus.engine.impl.util.iterator.ReversibleObjectArrayIterator;
import com.bdmajora.impetus.engine.impl.util.rand.SplitMixRandom;
import com.bdmajora.impetus.engine.impl.util.sorting.InsertionSort;
import com.bdmajora.impetus.engine.impl.util.sorting.MergeSort;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

// Tests the small utilities.
class UtilEdgeCasesTest {
    // Returns the Stafford variant 13 mix used as the reference.
    private static long mix13(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    // Matches a reference implementation of the generator.
    @Test
    void splitMixMatchesReference() {
        SplitMixRandom random = new SplitMixRandom(0);
        random.setState(5);
        long state = 5;
        for (int i = 0; i < 100; i++) {
            state += 0x9E3779B97F4A7C15L;
            assertEquals(mix13(state), random.nextLong());
        }
        random.setState(Long.MAX_VALUE);
        state = Long.MAX_VALUE + 0x9E3779B97F4A7C15L;
        assertEquals(mix13(state), random.nextLong());
    }

    // Keeps doubles and floats below one and at least zero.
    @Test
    void splitMixUnitIntervals() {
        SplitMixRandom random = new SplitMixRandom(3);
        for (int i = 0; i < 10000; i++) {
            double d = random.nextDouble();
            float f = random.nextFloat();
            assertTrue(d >= 0 && d < 1);
            assertTrue(f >= 0 && f < 1);
        }
    }

    // Covers both values of a boolean and spreads bounded values.
    @Test
    void splitMixBoundsAndBooleans() {
        SplitMixRandom random = new SplitMixRandom(11);
        boolean seenTrue = false, seenFalse = false;
        int[] counts = new int[7];
        for (int i = 0; i < 7000; i++) {
            if (random.nextBoolean()) seenTrue = true; else seenFalse = true;
            counts[random.nextInt(7)]++;
        }
        assertTrue(seenTrue && seenFalse);
        for (int count : counts) {
            assertTrue(count > 700 && count < 1300, Arrays.toString(counts));
        }
        assertEquals(0, random.nextInt(1));
        assertEquals(0L, random.nextLong(1L));
        assertThrows(IllegalArgumentException.class, () -> random.nextInt(0));
        assertThrows(IllegalArgumentException.class, () -> random.nextInt(-5));
        assertThrows(IllegalArgumentException.class, () -> random.nextLong(Long.MIN_VALUE));
        assertTrue(random.nextInt(Integer.MAX_VALUE) >= 0);
        assertTrue(random.nextLong(Long.MAX_VALUE) >= 0);
    }

    // Fills arrays of every residue length and handles empty and null arrays.
    @Test
    void splitMixBytes() {
        SplitMixRandom a = new SplitMixRandom(4);
        SplitMixRandom b = new SplitMixRandom(4);
        for (int length = 0; length <= 17; length++) {
            byte[] x = new byte[length];
            byte[] y = new byte[length];
            a.nextBytes(x);
            b.nextBytes(y);
            assertArrayEquals(x, y);
        }
        assertThrows(NullPointerException.class, () -> a.nextBytes(null));
        SplitMixRandom seeded = new SplitMixRandom(1);
        SplitMixRandom other = new SplitMixRandom(2);
        assertNotEquals(seeded.nextLong(), other.nextLong());
        seeded.setSeed(2);
        other.setSeed(2);
        assertEquals(seeded.nextInt(), other.nextInt());
    }

    // Sorts by descending key and keeps equal keys in order.
    @Test
    void insertionSortOrdersDescendingAndStably() {
        float[] keys = {1f, 3f, 3f, 2f, 3f};
        int[] indices = {0, 1, 2, 3, 4};
        InsertionSort.insertionSort(indices, 0, 5, keys);
        assertArrayEquals(new int[]{1, 2, 4, 3, 0}, indices);
    }

    // Sorts only the requested range.
    @Test
    void insertionSortRange() {
        float[] keys = {0f, 1f, 2f, 3f, 4f};
        int[] indices = {0, 1, 2, 3, 4};
        InsertionSort.insertionSort(indices, 1, 4, keys);
        assertArrayEquals(new int[]{0, 3, 2, 1, 4}, indices);
    }

    // Leaves empty and single element and inverted ranges untouched.
    @Test
    void insertionSortTrivialRanges() {
        float[] keys = {1f, 2f, 3f};
        int[] indices = {0, 1, 2};
        InsertionSort.insertionSort(indices, 0, 0, keys);
        InsertionSort.insertionSort(indices, 0, 1, keys);
        InsertionSort.insertionSort(indices, 2, 1, keys);
        InsertionSort.insertionSort(indices, 3, 3, keys);
        InsertionSort.insertionSort(indices, 0, Integer.MIN_VALUE, keys);
        InsertionSort.insertionSort(indices, Integer.MAX_VALUE, Integer.MIN_VALUE, keys);
        assertArrayEquals(new int[]{0, 1, 2}, indices);
        InsertionSort.insertionSort(new int[0], 0, 0, new float[0]);
    }

    // Rejects ranges outside the array before changing it.
    @Test
    void insertionSortRejectsBadRanges() {
        float[] keys = {1f, 2f, 3f};
        int[] indices = {0, 1, 2};
        assertThrows(IndexOutOfBoundsException.class, () -> InsertionSort.insertionSort(indices, 0, 4, keys));
        assertThrows(IndexOutOfBoundsException.class, () -> InsertionSort.insertionSort(indices, -1, 2, keys));
        assertThrows(IndexOutOfBoundsException.class, () -> InsertionSort.insertionSort(indices, 0, Integer.MAX_VALUE, keys));
        assertArrayEquals(new int[]{0, 1, 2}, indices);
        assertThrows(NullPointerException.class, () -> InsertionSort.insertionSort(null, 0, 2, keys));
        assertThrows(NullPointerException.class, () -> InsertionSort.insertionSort(indices, 0, 2, null));
    }

    // Matches a reference sort across sizes around the insertion threshold.
    @Test
    void mergeSortMatchesReference() {
        Random random = new Random(1);
        for (int size : new int[]{0, 1, 2, 15, 16, 17, 31, 32, 33, 100, 1000}) {
            float[] keys = new float[size];
            for (int i = 0; i < size; i++) {
                keys[i] = random.nextInt(10);
            }
            int[] sorted = MergeSort.mergeSort(keys);
            Integer[] expected = new Integer[size];
            for (int i = 0; i < size; i++) expected[i] = i;
            Arrays.sort(expected, (p, q) -> Float.compare(keys[q], keys[p]));
            assertArrayEquals(Arrays.stream(expected).mapToInt(Integer::intValue).toArray(), sorted, "size " + size);
        }
    }

    // Sorts presorted and reversed and constant inputs.
    @Test
    void mergeSortPatterns() {
        float[] up = new float[50];
        float[] down = new float[50];
        float[] flat = new float[50];
        for (int i = 0; i < 50; i++) {
            up[i] = i;
            down[i] = 50 - i;
        }
        int[] expectedUp = new int[50];
        int[] expectedDown = new int[50];
        int[] expectedFlat = new int[50];
        for (int i = 0; i < 50; i++) {
            expectedUp[i] = 49 - i;
            expectedDown[i] = i;
            expectedFlat[i] = i;
        }
        assertArrayEquals(expectedUp, MergeSort.mergeSort(up));
        assertArrayEquals(expectedDown, MergeSort.mergeSort(down));
        assertArrayEquals(expectedFlat, MergeSort.mergeSort(flat));
        assertThrows(NullPointerException.class, () -> MergeSort.mergeSort((float[]) null));
        assertThrows(NullPointerException.class, () -> MergeSort.mergeSort(null, new float[1]));
        assertThrows(NullPointerException.class, () -> MergeSort.mergeSort(new int[1], null));
    }

    // Sorts an index array in place.
    @Test
    void mergeSortInPlace() {
        float[] keys = new float[40];
        int[] indices = new int[40];
        for (int i = 0; i < 40; i++) {
            keys[i] = (i * 7) % 40;
            indices[i] = i;
        }
        MergeSort.mergeSort(indices, keys);
        for (int i = 1; i < 40; i++) {
            assertTrue(keys[indices[i - 1]] >= keys[indices[i]]);
        }
        MergeSort.mergeSort(new int[0], new float[0]);
    }

    // Reads unsigned bytes and stops at the count.
    @Test
    void byteIteratorReadsUnsigned() {
        ByteArrayIterator it = new ByteArrayIterator(new byte[]{(byte) 0xFF, 0, 1, 9}, 3);
        assertEquals(255, it.nextByteAsInt());
        assertEquals(0, it.nextByteAsInt());
        assertEquals(1, it.nextByteAsInt());
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::nextByteAsInt);
    }

    // Handles empty and negative counts and rejects a count past the array.
    @Test
    void byteIteratorBounds() {
        assertFalse(new ByteArrayIterator(new byte[0], 0).hasNext());
        assertFalse(new ByteArrayIterator(new byte[3], 0).hasNext());
        assertFalse(new ByteArrayIterator(new byte[3], -1).hasNext());
        assertFalse(new ByteArrayIterator(new byte[3], Integer.MIN_VALUE).hasNext());
        assertTrue(new ByteArrayIterator(new byte[3], 3).hasNext());
        assertThrows(IndexOutOfBoundsException.class, () -> new ByteArrayIterator(new byte[3], 4));
        assertThrows(IndexOutOfBoundsException.class, () -> new ByteArrayIterator(new byte[3], Integer.MAX_VALUE));
        assertThrows(NullPointerException.class, () -> new ByteArrayIterator(null, 0));
    }

    // Walks a range forward and backward.
    @Test
    void reversibleIteratorDirections() {
        String[] array = {"a", "b", "c", "d"};
        assertEquals(List.of("b", "c"), drain(new ReversibleObjectArrayIterator<>(array, 1, 3, false)));
        assertEquals(List.of("c", "b"), drain(new ReversibleObjectArrayIterator<>(array, 1, 3, true)));
        assertEquals(List.of("a", "b", "c", "d"), drain(new ReversibleObjectArrayIterator<>(array, 0, 4, false)));
        assertEquals(List.of("d", "c", "b", "a"), drain(new ReversibleObjectArrayIterator<>(array, 0, 4, true)));
        assertEquals(List.of("d"), drain(new ReversibleObjectArrayIterator<>(array, 3, 4, true)));
    }

    // Reads only the used part of a list and handles empty lists.
    @Test
    void reversibleIteratorList() {
        ObjectArrayList<String> list = new ObjectArrayList<>(10);
        list.add("x");
        list.add("y");
        assertEquals(List.of("y", "x"), drain(new ReversibleObjectArrayIterator<>(list, true)));
        assertEquals(List.of("x", "y"), drain(new ReversibleObjectArrayIterator<>(list, false)));
        assertEquals(List.of(), drain(new ReversibleObjectArrayIterator<>(new ObjectArrayList<String>(), true)));
    }

    // Treats empty and inverted ranges as empty and rejects bad ranges.
    @Test
    void reversibleIteratorBadRanges() {
        String[] array = {"a", "b"};
        assertFalse(new ReversibleObjectArrayIterator<>(array, 1, 1, false).hasNext());
        assertFalse(new ReversibleObjectArrayIterator<>(array, 2, 0, true).hasNext());
        assertFalse(new ReversibleObjectArrayIterator<>(array, 5, 4, true).hasNext());
        assertFalse(new ReversibleObjectArrayIterator<>(array, Integer.MAX_VALUE, Integer.MIN_VALUE, false).hasNext());
        assertThrows(IndexOutOfBoundsException.class, () -> new ReversibleObjectArrayIterator<>(array, 0, 3, false));
        assertThrows(IndexOutOfBoundsException.class, () -> new ReversibleObjectArrayIterator<>(array, -1, 1, false));
        assertThrows(NullPointerException.class, () -> new ReversibleObjectArrayIterator<>((String[]) null, 0, 0, false));
        assertThrows(NoSuchElementException.class, () -> new ReversibleObjectArrayIterator<>(array, 0, 0, false).next());
    }

    // Drains an iterator into a list.
    private static <T> List<T> drain(java.util.Iterator<T> iterator) {
        List<T> out = new ArrayList<>();
        iterator.forEachRemaining(out::add);
        return out;
    }

    // Treats the origin as inside and the far edge as outside.
    @Test
    void rectPointContainment() {
        Rect2i rect = new Rect2i(2, 3, 4, 5);
        assertTrue(rect.contains(2, 3));
        assertTrue(rect.contains(5, 7));
        assertFalse(rect.contains(6, 7));
        assertFalse(rect.contains(5, 8));
        assertFalse(rect.contains(1, 3));
        assertFalse(rect.contains(2, 2));
    }

    // Avoids overflow at the int limits.
    @Test
    void rectContainmentAtLimits() {
        Rect2i wide = new Rect2i(Integer.MAX_VALUE - 1, 0, Integer.MAX_VALUE, 1);
        assertTrue(wide.contains(Integer.MAX_VALUE, 0));
        assertFalse(wide.contains(Integer.MIN_VALUE, 0));
        Rect2i negative = new Rect2i(Integer.MIN_VALUE, Integer.MIN_VALUE, 10, 10);
        assertTrue(negative.contains(Integer.MIN_VALUE, Integer.MIN_VALUE));
        assertFalse(negative.contains(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertFalse(new Rect2i(0, 0, 0, 0).contains(0, 0));
        assertFalse(new Rect2i(0, 0, -3, 5).contains(-1, 0));
        assertFalse(new Rect2i(0, 0, -3, 5).contains(0, 0));
    }

    // Checks rectangle containment on every edge.
    @Test
    void rectContainsRect() {
        Rect2i outer = new Rect2i(0, 0, 10, 10);
        assertTrue(outer.contains(outer));
        assertTrue(outer.contains(new Rect2i(2, 2, 3, 3)));
        assertTrue(outer.contains(new Rect2i(9, 9, 1, 1)));
        assertFalse(outer.contains(new Rect2i(9, 9, 2, 1)));
        assertFalse(outer.contains(new Rect2i(-1, 0, 2, 2)));
        assertFalse(outer.contains(new Rect2i(0, -1, 2, 2)));
        assertFalse(outer.contains(new Rect2i(0, 0, 11, 10)));
        assertFalse(outer.contains(new Rect2i(0, 0, 10, 11)));
        assertTrue(outer.contains(new Rect2i(10, 10, 0, 0)));
        assertFalse(outer.contains(new Rect2i(11, 10, 0, 0)));
        assertFalse(outer.contains(new Rect2i(2, 2, -1, 3)));
        assertFalse(outer.contains(new Rect2i(Integer.MAX_VALUE, 0, 5, 5)));
        assertFalse(outer.contains(new Rect2i(0, 0, Integer.MAX_VALUE, 1)));
        assertThrows(NullPointerException.class, () -> outer.contains((Rect2i) null));
    }

    // Copies and compares by value and class.
    @Test
    void rectEqualityAndCopy() {
        Rect2i rect = new Rect2i(1, 2, 3, 4);
        Rect2i copy = new Rect2i(rect);
        assertEquals(rect, copy);
        assertEquals(rect.hashCode(), copy.hashCode());
        assertEquals(Objects.hash(1, 2, 3, 4), rect.hashCode());
        assertNotEquals(rect, new Rect2i(1, 2, 3, 5));
        assertNotEquals(rect, new Rect2i(0, 2, 3, 4));
        assertNotEquals(rect, new Rect2i(1, 0, 3, 4));
        assertNotEquals(rect, new Rect2i(1, 2, 0, 4));
        assertNotEquals(rect, null);
        assertNotEquals(rect, "x");
        assertEquals("Rect2i[x=1, y=2, width=3, height=4]", rect.toString());
        assertThrows(NullPointerException.class, () -> new Rect2i((Rect2i) null));
        assertEquals(1, rect.x());
        assertEquals(2, rect.y());
        assertEquals(3, rect.width());
        assertEquals(4, rect.height());
    }

    // Exposes the origin point.
    @Test
    void pointZero() {
        assertEquals(0, Point2i.ZERO.x());
        assertEquals(0, Point2i.ZERO.y());
    }
}
