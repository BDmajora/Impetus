package com.bdmajora.coarctatio.dedup;

import com.bdmajora.coarctatio.CoarctatioConfig;

// Pools for resource location parts.
public final class ResourceLocationCaches {
    // Pool of resource domains.
    public static final DeduplicationCache<String> DOMAINS =
            new DeduplicationCache<>("Resource domains", CoarctatioConfig.get().poolSizeLimit);

    // Pool of resource paths.
    public static final DeduplicationCache<String> PATHS =
            new DeduplicationCache<>("Resource paths", CoarctatioConfig.get().poolSizeLimit);

    // Prevents instantiation
    private ResourceLocationCaches() {
    }
}
