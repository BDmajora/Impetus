/*
 * Copyright (C) 2002-2017 Sebastiano Vigna
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 *
 * For the sorting and binary search code:
 *
 * Copyright (C) 1999 CERN - European Organization for Nuclear Research.
 *
 *   Permission to use, copy, modify, distribute and sell this software and
 *   its documentation for any purpose is hereby granted without fee,
 *   provided that the above copyright notice appear in all copies and that
 *   both that copyright notice and this permission notice appear in
 *   supporting documentation. CERN makes no representations about the
 *   suitability of this software for any purpose. It is provided "as is"
 *   without expressed or implied warranty.
 */
package com.bdmajora.impetus.engine.impl.util.sorting;

// Merge sort of index arrays by float keys based on the fastutil int array sort.
public class MergeSort {
    // Length below which a range is sorted by insertion.
    private static final int INSERTION_SORT_THRESHOLD = 16;

    // Returns the indices of the keys ordered by descending key.
    public static int[] mergeSort(float[] keys) {
        int[] indices = new int[keys.length];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }
        mergeSort(indices, keys);
        return indices;
    }

    // Sorts an index array by descending key in place.
    public static void mergeSort(final int[] indices, final float[] keys) {
        java.util.Objects.requireNonNull(indices, "indices");
        java.util.Objects.requireNonNull(keys, "keys");
        mergeSort(indices, keys, 0, indices.length, null);
    }

    // Sorts a range with a top down merge and insertion sort for short ranges.
    private static void mergeSort(final int[] indices, final float[] keys, final int fromIndex, final int toIndex, int[] supp) {
        int len = toIndex - fromIndex;

        // Sorts short ranges by insertion
        if (len < INSERTION_SORT_THRESHOLD) {
            InsertionSort.insertionSort(indices, fromIndex, toIndex, keys);
            return;
        }

        if (supp == null) {
            supp = indices.clone();
        }

        // Sorts both halves into the support array
        final int mid = (fromIndex + toIndex) >>> 1;
        mergeSort(supp, keys, fromIndex, mid, indices);
        mergeSort(supp, keys, mid, toIndex, indices);

        // Copies back when the halves are already in order
        if (keys[supp[mid]] <= keys[supp[mid - 1]]) {
            System.arraycopy(supp, fromIndex, indices, fromIndex, len);
            return;
        }

        // Merges the halves into the index array
        int i = fromIndex, p = fromIndex, q = mid;

        while (i < toIndex) {
            if (q >= toIndex || p < mid && keys[supp[q]] <= keys[supp[p]]) {
                indices[i] = supp[p++];
            } else {
                indices[i] = supp[q++];
            }

            i++;
        }
    }
}
