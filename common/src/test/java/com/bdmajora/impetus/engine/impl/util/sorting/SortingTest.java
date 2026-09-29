package com.bdmajora.impetus.engine.impl.util.sorting;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SortingTest {
    private static void assertDescending(int[] indices, float[] keys, int from, int to) {
        for (int i = from + 1; i < to; i++) {
            assertTrue(keys[indices[i - 1]] >= keys[indices[i]], "index " + i);
        }
    }

    @Test
    void insertionSortOrdersByKeyDescending() {
        float[] keys = {3, 1, 2, 5, 4};
        int[] indices = {0, 1, 2, 3, 4};
        InsertionSort.insertionSort(indices, 0, 5, keys);
        assertDescending(indices, keys, 0, 5);
        assertEquals(3, indices[0]);
        int[] partial = {0, 1, 2, 3, 4};
        InsertionSort.insertionSort(partial, 1, 4, keys);
        assertDescending(partial, keys, 1, 4);
        assertEquals(0, partial[0]);
        assertEquals(4, partial[4]);
        new InsertionSort();
    }

    @Test
    void mergeSortHandlesLargeRandomAndPresortedInputs() {
        Random random = new Random(42);
        float[] keys = new float[500];
        int[] indices = new int[500];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = random.nextFloat();
            indices[i] = i;
        }
        MergeSort.mergeSort(indices, keys);
        assertDescending(indices, keys, 0, keys.length);

        float[] sorted = new float[64];
        int[] order = new int[64];
        for (int i = 0; i < 64; i++) {
            sorted[i] = 64 - i;
            order[i] = i;
        }
        MergeSort.mergeSort(order, sorted);
        assertDescending(order, sorted, 0, 64);
        new MergeSort();
    }
}
