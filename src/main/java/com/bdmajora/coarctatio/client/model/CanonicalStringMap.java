package com.bdmajora.coarctatio.client.model;

import com.bdmajora.coarctatio.dedup.DeduplicationCache;

import java.util.HashMap;
import java.util.Map;

// A HashMap that interns every key and value through a pool as they go in, so a map filled by a deserializer and appended to by mods holds one instance per distinct string
public final class CanonicalStringMap extends HashMap<String, String> {
    private final DeduplicationCache<String> pool;

    public CanonicalStringMap(Map<String, String> source, DeduplicationCache<String> pool) {
        super(Math.max(4, source.size() * 4 / 3 + 1));
        this.pool = pool;
        putAll(source);
    }

    @Override
    public String put(String key, String value) {
        return super.put(this.pool.deduplicate(key), this.pool.deduplicate(value));
    }

    // HashMap.putAll bypasses put, so the interning is repeated here
    @Override
    public void putAll(Map<? extends String, ? extends String> map) {
        for (Map.Entry<? extends String, ? extends String> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }
}
