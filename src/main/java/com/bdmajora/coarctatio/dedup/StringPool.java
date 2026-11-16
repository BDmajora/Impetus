package com.bdmajora.coarctatio.dedup;

import com.bdmajora.coarctatio.CoarctatioConfig;

// Pools for strings shared across the game.
public final class StringPool {
    // Pool of compound tag keys.
    public static final ShardedStringCache NBT_KEYS =
            new ShardedStringCache("NBT keys", CoarctatioConfig.get().poolSizeLimit);

    // Pool of loader names.
    public static final ShardedStringCache LOADER =
            new ShardedStringCache("Loader names", CoarctatioConfig.get().poolSizeLimit);

    // Prevents instantiation
    private StringPool() {
    }
}
