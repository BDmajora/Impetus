package com.bdmajora.coarctatio.client.model;

import com.bdmajora.coarctatio.dedup.DeduplicationCache;

import java.util.HashMap;
import java.util.Map;

// Hash map that stores pooled instances of its keys and values.
public final class CanonicalStringMap extends HashMap<String, String> {
    // Pool that supplies the shared instances.
    private final DeduplicationCache<String> pool;

    // Creates a map holding pooled copies of the source entries.
    public CanonicalStringMap(Map<String, String> source, DeduplicationCache<String> pool) {
        super(Math.max(4, source.size() * 4 / 3 + 1));
        this.pool = pool;
        putAll(source);
    }

    // Stores an entry using pooled instances of the key and value.
    @Override
    public String put(String key, String value) {
        return super.put(this.pool.deduplicate(key), this.pool.deduplicate(value));
    }

    // Stores every source entry using pooled instances.
    @Override
    public void putAll(Map<? extends String, ? extends String> map) {
        for (Map.Entry<? extends String, ? extends String> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }
}
