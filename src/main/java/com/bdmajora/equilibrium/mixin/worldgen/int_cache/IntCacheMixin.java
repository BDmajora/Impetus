package com.bdmajora.equilibrium.mixin.worldgen.int_cache;

import com.bdmajora.equilibrium.common.world.IntCachePool;
import net.minecraft.world.gen.layer.IntCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;


// The biome layer scratch pool per thread instead of one global pool behind a monitor: resetIntCache hands every in-use array back, so two threads walking layers at once (a spawn pass and a chunk generating, two dimensions) would reclaim each other's buffers mid-use; per thread the reuse pattern is exactly vanilla's, without the lock
@Mixin(IntCache.class)
public abstract class IntCacheMixin {
    @Unique
    private static final ThreadLocal<IntCachePool> equilibrium$pool = ThreadLocal.withInitial(IntCachePool::new);

    /**
     * @author bdmajora
     * @reason per-thread pools, same allocation and reuse behaviour as vanilla
     */
    @Overwrite
    public static int[] getIntCache(int size) {
        IntCachePool pool = equilibrium$pool.get();
        if (size <= 256) {
            if (pool.freeSmall.isEmpty()) {
                int[] array = new int[256];
                pool.inUseSmall.add(array);
                return array;
            }
            int[] array = pool.freeSmall.remove(pool.freeSmall.size() - 1);
            pool.inUseSmall.add(array);
            return array;
        }
        if (size > pool.largeSize) {
            pool.largeSize = size;
            pool.freeLarge.clear();
            pool.inUseLarge.clear();
            int[] array = new int[pool.largeSize];
            pool.inUseLarge.add(array);
            return array;
        }
        if (pool.freeLarge.isEmpty()) {
            int[] array = new int[pool.largeSize];
            pool.inUseLarge.add(array);
            return array;
        }
        int[] array = pool.freeLarge.remove(pool.freeLarge.size() - 1);
        pool.inUseLarge.add(array);
        return array;
    }

    /**
     * @author bdmajora
     * @reason per-thread pools, same allocation and reuse behaviour as vanilla
     */
    @Overwrite
    public static void resetIntCache() {
        IntCachePool pool = equilibrium$pool.get();
        if (!pool.freeLarge.isEmpty()) {
            pool.freeLarge.remove(pool.freeLarge.size() - 1);
        }
        if (!pool.freeSmall.isEmpty()) {
            pool.freeSmall.remove(pool.freeSmall.size() - 1);
        }
        pool.freeLarge.addAll(pool.inUseLarge);
        pool.freeSmall.addAll(pool.inUseSmall);
        pool.inUseLarge.clear();
        pool.inUseSmall.clear();
    }

    /**
     * @author bdmajora
     * @reason per-thread pools, same allocation and reuse behaviour as vanilla
     */
    @Overwrite
    public static String getCacheSizes() {
        IntCachePool pool = equilibrium$pool.get();
        return "cache: " + pool.freeLarge.size() + ", tcache: " + pool.freeSmall.size()
                + ", allocated: " + pool.inUseLarge.size() + ", tallocated: " + pool.inUseSmall.size();
    }
}
