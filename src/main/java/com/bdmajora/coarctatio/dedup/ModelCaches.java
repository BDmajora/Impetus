package com.bdmajora.coarctatio.dedup;

import com.bdmajora.coarctatio.CoarctatioConfig;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.ints.IntArrays;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

// Pools for data shared between baked models.
public final class ModelCaches {
    // Pool of quad vertex arrays.
    public static final DeduplicationCache<int[]> QUADS =
            new DeduplicationCache<>("Quad vertex data", CoarctatioConfig.get().poolSizeLimit,
                    IntArrays.HASH_STRATEGY);

    // Pool of model variant names.
    public static final DeduplicationCache<String> VARIANTS =
            new DeduplicationCache<>("Model variants", CoarctatioConfig.get().poolSizeLimit);

    // Pool of face coordinate arrays.
    public static final DeduplicationCache<float[]> FACE_UVS =
            new DeduplicationCache<>("Face UVs", CoarctatioConfig.get().poolSizeLimit, FloatArrays.HASH_STRATEGY);

    // Pool of texture names.
    public static final DeduplicationCache<String> MODEL_TEXTURES =
            new DeduplicationCache<>("Model texture names", CoarctatioConfig.get().poolSizeLimit);

    // Count of skipped quads by the class that produced them.
    private static final Map<String, Integer> SKIPPED_BY_CLASS = new HashMap<>();

    // Prevents instantiation
    private ModelCaches() {
    }

    // Counts one skipped quad of the type.
    public static void recordSkippedQuad(Class<?> type) {
        String name = type.getName();

        synchronized (SKIPPED_BY_CLASS) {
            SKIPPED_BY_CLASS.merge(name, 1, Integer::sum);
        }
    }

    // Returns the five most frequent skipped quad classes.
    public static String skippedSummary() {
        synchronized (SKIPPED_BY_CLASS) {
            if (SKIPPED_BY_CLASS.isEmpty()) {
                return "none";
            }

            return SKIPPED_BY_CLASS.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(5)
                    .map(entry -> entry.getValue() + "x " + entry.getKey())
                    .collect(Collectors.joining(", "));
        }
    }

    // Opens every pool and clears the skipped counts.
    public static void open() {
        QUADS.open();
        VARIANTS.open();
        FACE_UVS.open();
        MODEL_TEXTURES.open();

        synchronized (SKIPPED_BY_CLASS) {
            SKIPPED_BY_CLASS.clear();
        }
    }

    // Closes every pool except the variant names that stay open for the session.
    public static void close() {
        QUADS.close();
        FACE_UVS.close();
        MODEL_TEXTURES.close();
    }
}
