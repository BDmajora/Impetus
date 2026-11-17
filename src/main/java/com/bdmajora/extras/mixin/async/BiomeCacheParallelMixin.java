package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.biome.BiomeCache;
import org.spongepowered.asm.mixin.Mixin;

// Mixin that changes BiomeCache.
@Mixin(BiomeCache.class)
public abstract class BiomeCacheParallelMixin {
    // Reads a biome cache entry under the cache lock.
    @WrapMethod(method = "getEntry")
    private BiomeCache.Block impetus$lockedGetEntry(int x, int z, Operation<BiomeCache.Block> original) {
        if (!ParallelProcessor.INSTALLED) {
            return original.call(x, z);
        }
        synchronized (this) {
            return original.call(x, z);
        }
    }

    // Drops stale biome cache entries under the cache lock.
    @WrapMethod(method = "cleanupCache")
    private void impetus$lockedCleanup(Operation<Void> original) {
        if (!ParallelProcessor.INSTALLED) {
            original.call();
            return;
        }
        synchronized (this) {
            original.call();
        }
    }
}
