package com.bdmajora.coarctatio.client.model.dynamic;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import net.minecraft.client.renderer.block.model.IBakedModel;

import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;

// Bounded cache of baked models by key identity.
public final class ModelLookupCache<K> {
    // Largest number of cached entries.
    private static final int CAPACITY = 1000;

    // Cached models in order of use.
    private final Reference2ReferenceLinkedOpenHashMap<K, IBakedModel> cache = new Reference2ReferenceLinkedOpenHashMap<>();
    // Lock that guards the cache.
    private final StampedLock lock = new StampedLock();
    // Function that produces a missing model.
    private final Function<K, IBakedModel> loader;
    // Whether a missing model is cached as null.
    private final boolean cacheNulls;

    // Creates a cache and rejects a null loader.
    public ModelLookupCache(Function<K, IBakedModel> loader, boolean cacheNulls) {
        this.loader = java.util.Objects.requireNonNull(loader, "loader");
        this.cacheNulls = cacheNulls;
    }

    // Removes every entry.
    public void clear() {
        long stamp = this.lock.writeLock();
        try {
            this.cache.clear();
        } finally {
            this.lock.unlock(stamp);
        }
    }

    // Returns the cached model or loads and stores it.
    public IBakedModel get(K key) {
        IBakedModel model = this.read(key);
        if (model == null && (!this.cacheNulls || !this.contains(key))) {
            model = this.load(key);
        }
        return model;
    }

    // Returns the cached model or null.
    private IBakedModel read(K key) {
        long stamp = this.lock.readLock();
        try {
            return this.cache.get(key);
        } finally {
            this.lock.unlock(stamp);
        }
    }

    // Returns whether the key is cached.
    private boolean contains(K key) {
        long stamp = this.lock.readLock();
        try {
            return this.cache.containsKey(key);
        } finally {
            this.lock.unlock(stamp);
        }
    }

    // Returns the loaded model and stores it unless it is null and nulls are not cached.
    private IBakedModel load(K key) {
        IBakedModel model = this.loader.apply(key);
        if (model == null && !this.cacheNulls) {
            return null;
        }
        long stamp = this.lock.writeLock();
        try {
            this.cache.putAndMoveToFirst(key, model);
            if (this.cache.size() > CAPACITY) {
                this.cache.removeLast();
            }
        } finally {
            this.lock.unlock(stamp);
        }
        return model;
    }
}
