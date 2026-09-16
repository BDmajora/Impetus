package com.bdmajora.coarctatio.client.model.dynamic;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceLinkedOpenHashMap;
import net.minecraft.client.renderer.block.model.IBakedModel;

import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;

// Identity-keyed LRU in front of the baked provider's Guava cache, which is too slow to hit on every item render; keys are the per-item location objects, which are unique per registration, so reference lookup is correct
public final class ModelLookupCache<K> {
    private static final int CAPACITY = 1000;

    private final Reference2ReferenceLinkedOpenHashMap<K, IBakedModel> cache = new Reference2ReferenceLinkedOpenHashMap<>();
    private final StampedLock lock = new StampedLock();
    private final Function<K, IBakedModel> loader;
    // A null result is a real answer for item lookups (vanilla's mesher returns null for an unregistered meta), so it is cached too
    private final boolean cacheNulls;

    public ModelLookupCache(Function<K, IBakedModel> loader, boolean cacheNulls) {
        this.loader = loader;
        this.cacheNulls = cacheNulls;
    }

    public void clear() {
        long stamp = this.lock.writeLock();
        try {
            this.cache.clear();
        } finally {
            this.lock.unlock(stamp);
        }
    }

    public IBakedModel get(K key) {
        IBakedModel model = this.read(key);
        if (model == null && (!this.cacheNulls || !this.contains(key))) {
            model = this.load(key);
        }
        return model;
    }

    private IBakedModel read(K key) {
        long stamp = this.lock.readLock();
        try {
            return this.cache.get(key);
        } finally {
            this.lock.unlock(stamp);
        }
    }

    private boolean contains(K key) {
        long stamp = this.lock.readLock();
        try {
            return this.cache.containsKey(key);
        } finally {
            this.lock.unlock(stamp);
        }
    }

    // The load runs outside the lock since it may bake, and baking may ask this cache for another model
    private IBakedModel load(K key) {
        IBakedModel model = this.loader.apply(key);
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
