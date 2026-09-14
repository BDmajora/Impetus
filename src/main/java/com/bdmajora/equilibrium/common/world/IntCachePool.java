package com.bdmajora.equilibrium.common.world;

import java.util.ArrayList;
import java.util.List;

// One thread's share of the biome layer scratch arrays: vanilla IntCache's four lists and its large-array size, owned by the thread instead of the class
public final class IntCachePool {
    public int largeSize = 256;
    public final List<int[]> freeSmall = new ArrayList<>();
    public final List<int[]> inUseSmall = new ArrayList<>();
    public final List<int[]> freeLarge = new ArrayList<>();
    public final List<int[]> inUseLarge = new ArrayList<>();
}
