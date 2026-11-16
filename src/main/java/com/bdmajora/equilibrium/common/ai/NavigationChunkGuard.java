package com.bdmajora.equilibrium.common.ai;

// Marks the thread that is building a navigation cache.
public final class NavigationChunkGuard {
    // Whether the thread is building a cache.
    private static final ThreadLocal<Boolean> BUILDING_NAVIGATION_CACHE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    // Prevents instantiation
    private NavigationChunkGuard() {
    }

    // Returns whether the thread is building a cache.
    public static boolean isNavigating() {
        return BUILDING_NAVIGATION_CACHE.get();
    }

    // Stores whether the thread is building a cache.
    public static void set(boolean navigating) {
        BUILDING_NAVIGATION_CACHE.set(navigating);
    }
}
