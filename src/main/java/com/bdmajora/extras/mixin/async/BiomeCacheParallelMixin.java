package com.bdmajora.extras.mixin.async;

import com.bdmajora.extras.async.ParallelProcessor;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.biome.BiomeCache;
import org.spongepowered.asm.mixin.Mixin;

// The per-provider biome cache is an open-hash map plus a list, filled on miss; entity AI and the spawner read biomes from any thread while the server tick expires entries, so both paths share the cache's monitor
@Mixin(BiomeCache.class)
public abstract class BiomeCacheParallelMixin {
    @WrapMethod(method = "getEntry")
    private BiomeCache.Block impetus$lockedGetEntry(int x, int z, Operation<BiomeCache.Block> original) {
        if (!ParallelProcessor.INSTALLED) {
            return original.call(x, z);
        }
        synchronized (this) {
            return original.call(x, z);
        }
    }

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
