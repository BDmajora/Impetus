package com.bdmajora.equilibrium.common.ai;

// Marks the current thread as building a pathfinding ChunkCache so the cache constructor knows to refuse unloaded chunks; a thread-local rather than a static because parallel ticking may path several mobs at once
public final class NavigationChunkGuard {
    private static final ThreadLocal<Boolean> BUILDING_NAVIGATION_CACHE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private NavigationChunkGuard() {
    }

    public static boolean isNavigating() {
        return BUILDING_NAVIGATION_CACHE.get();
    }

    public static void set(boolean navigating) {
        BUILDING_NAVIGATION_CACHE.set(navigating);
    }
}
