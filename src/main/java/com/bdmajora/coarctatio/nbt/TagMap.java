package com.bdmajora.coarctatio.nbt;

import com.bdmajora.coarctatio.CoarctatioConfig;
import com.bdmajora.coarctatio.dedup.StringPool;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.NBTBase;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

// Tag map that starts as an array map and grows into a hash map.
public class TagMap implements Map<String, NBTBase> {
    // Entry count at which the array map becomes a hash map.
    private final int promotionThreshold;
    // Whether keys are replaced by pooled instances.
    private final boolean internKeys;

    // Map that holds the entries.
    private Map<String, NBTBase> delegate;

    // Creates an empty map using the configured threshold.
    public TagMap() {
        CoarctatioConfig config = CoarctatioConfig.get();

        this.promotionThreshold = config.nbtArrayMapThreshold;
        this.internKeys = config.internNbtKeys;
        this.delegate = this.promotionThreshold > 0
                ? new Object2ObjectArrayMap<>()
                : new Object2ObjectOpenHashMap<>();
    }

    // Stores the tag and promotes the map to a hash map when the threshold is reached.
    @Override
    public NBTBase put(String key, NBTBase value) {
        if (this.internKeys) {
            key = StringPool.NBT_KEYS.deduplicate(key);
        }

        if (this.promotionThreshold > 0
                && this.delegate.size() >= this.promotionThreshold
                && this.delegate instanceof Object2ObjectArrayMap
                && !this.delegate.containsKey(key)) {
            this.delegate = new Object2ObjectOpenHashMap<>(this.delegate);
        }

        return this.delegate.put(key, value);
    }

    // Stores every entry.
    @Override
    public void putAll(Map<? extends String, ? extends NBTBase> m) {
        for (Map.Entry<? extends String, ? extends NBTBase> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    // Removes every entry.
    @Override
    public void clear() {
        this.delegate = this.promotionThreshold > 0
                ? new Object2ObjectArrayMap<>()
                : new Object2ObjectOpenHashMap<>();
    }

    // Returns the number of entries.
    @Override
    public int size() {
        return this.delegate.size();
    }

    // Returns whether the map holds no entries.
    @Override
    public boolean isEmpty() {
        return this.delegate.isEmpty();
    }

    // Returns whether the key is present.
    @Override
    public boolean containsKey(Object key) {
        return this.delegate.containsKey(key);
    }

    // Returns whether the tag is present.
    @Override
    public boolean containsValue(Object value) {
        return this.delegate.containsValue(value);
    }

    // Returns the tag of the key or null when absent.
    @Override
    public NBTBase get(Object key) {
        return this.delegate.get(key);
    }

    // Removes the key and returns its tag.
    @Override
    public NBTBase remove(Object key) {
        return this.delegate.remove(key);
    }

    // Returns the live key view.
    @Override
    public Set<String> keySet() {
        return this.delegate.keySet();
    }

    // Returns the live tag view.
    @Override
    public Collection<NBTBase> values() {
        return this.delegate.values();
    }

    // Returns the live entry view.
    @Override
    public Set<Entry<String, NBTBase>> entrySet() {
        return this.delegate.entrySet();
    }

    // Returns whether the object is equal to the entries.
    @Override
    public boolean equals(Object obj) {
        return obj == this || this.delegate.equals(obj);
    }

    // Returns the hash of the entries.
    @Override
    public int hashCode() {
        return this.delegate.hashCode();
    }

    // Returns the entries as text.
    @Override
    public String toString() {
        return this.delegate.toString();
    }
}
