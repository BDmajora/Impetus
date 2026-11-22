package com.bdmajora.coarctatio.dedup;

import com.bdmajora.coarctatio.CoarctatioConfig;
import it.unimi.dsi.fastutil.floats.FloatArrays;

// Pools for data shared between model parts.
public final class ModelCaches {
    // Pool of face coordinate arrays.
    public static final DeduplicationCache<float[]> FACE_UVS =
            new DeduplicationCache<>("Face UVs", CoarctatioConfig.get().poolSizeLimit, FloatArrays.HASH_STRATEGY);

    // Pool of texture names.
    public static final DeduplicationCache<String> MODEL_TEXTURES =
            new DeduplicationCache<>("Model texture names", CoarctatioConfig.get().poolSizeLimit);

    // Prevents instantiation
    private ModelCaches() {
    }

    // Opens every pool.
    public static void open() {
        FACE_UVS.open();
        MODEL_TEXTURES.open();
    }

    // Closes every pool.
    public static void close() {
        FACE_UVS.close();
        MODEL_TEXTURES.close();
    }
}
