package com.bdmajora.coarctatio.collections;

import java.util.List;

// Helpers for building compact collections.
public final class CollectionHelper {
    // Prevents instantiation
    private CollectionHelper() {
    }

    // Returns a fixed size copy of the list or the same list when it is null or already fixed.
    public static <T> List<T> fixed(List<T> src) {
        if (src == null || src instanceof FixedArrayList) {
            return src;
        }

        return new FixedArrayList<>(src);
    }
}
