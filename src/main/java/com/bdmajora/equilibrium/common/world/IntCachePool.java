package com.bdmajora.equilibrium.common.world;

import java.util.ArrayList;
import java.util.List;

// Pool of reusable int arrays.
public final class IntCachePool {
    // Length of large arrays.
    public int largeSize = 256;
    // Small arrays that are free.
    public final List<int[]> freeSmall = new ArrayList<>();
    // Small arrays that are handed out.
    public final List<int[]> inUseSmall = new ArrayList<>();
    // Large arrays that are free.
    public final List<int[]> freeLarge = new ArrayList<>();
    // Large arrays that are handed out.
    public final List<int[]> inUseLarge = new ArrayList<>();
}
