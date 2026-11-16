package com.bdmajora.impetus.engine.impl.util.sorting;

import java.util.Objects;

// Insertion sort of index arrays by float keys.
public class InsertionSort {
    // Sorts a range of indices by descending key and keeps the order of equal keys.
    public static void insertionSort(final int[] indices, final int fromIndex, final int toIndex, final float[] keys) {
        Objects.requireNonNull(indices, "indices");
        Objects.requireNonNull(keys, "keys");

        // Ranges of fewer than two elements stay unchanged
        if ((long) toIndex - fromIndex < 2) {
            return;
        }

        Objects.checkFromToIndex(fromIndex, toIndex, indices.length);

        for (int index = fromIndex + 1; index < toIndex; index++) {
            int t = indices[index];
            float key = keys[t];
            int j = index;

            while (j > fromIndex) {
                int u = indices[j - 1];

                if (!(keys[u] < key)) {
                    break;
                }

                indices[j] = u;
                j--;
            }

            indices[j] = t;
        }
    }
}
