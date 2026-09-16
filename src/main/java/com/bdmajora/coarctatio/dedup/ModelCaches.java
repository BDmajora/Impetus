package com.bdmajora.coarctatio.dedup;

import com.bdmajora.coarctatio.CoarctatioConfig;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.ints.IntArrays;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

// Bake-scoped pools, opened and closed around a resource reload by Coarctatio
public final class ModelCaches {
    // The largest saving: every full block face bakes to a 28-int array and most are byte-identical, so IntArrays.HASH_STRATEGY (contents, not references) collapses millions into tens of thousands; arrays handed out are immutable, see CoarctatioBakedQuadMixin
    public static final DeduplicationCache<int[]> QUADS =
            new DeduplicationCache<>("Quad vertex data", CoarctatioConfig.get().poolSizeLimit,
                    IntArrays.HASH_STRATEGY);

    // The variant string of a ModelResourceLocation ("normal", "inventory", "facing=north,half=bottom"); the first two alone are a large fraction of all instances
    public static final DeduplicationCache<String> VARIANTS =
            new DeduplicationCache<>("Model variants", CoarctatioConfig.get().poolSizeLimit);

    // A face's four UV floats, read by the baker and never written after construction; nearly every face of every model uses one of a few dozen (StellarCore's BlockFaceUV canonicalization)
    public static final DeduplicationCache<float[]> FACE_UVS =
            new DeduplicationCache<>("Face UVs", CoarctatioConfig.get().poolSizeLimit, FloatArrays.HASH_STRATEGY);

    // The texture variable names and paths of an unbaked model ("particle", "#all", "blocks/stone"), repeated by every model that uses them
    public static final DeduplicationCache<String> MODEL_TEXTURES =
            new DeduplicationCache<>("Model texture names", CoarctatioConfig.get().poolSizeLimit);

    // Quads the immutability rule refused, per class; instrumentation that tells "pool is empty" apart from "injection never fired" and "every quad was a subclass"
    private static final Map<String, Integer> SKIPPED_BY_CLASS = new HashMap<>();

    private ModelCaches() {
    }

    // Called from the quad mixin's reject path; synchronised because quad construction is not thread-confined once mods are involved
    public static void recordSkippedQuad(Class<?> type) {
        String name = type.getName();

        synchronized (SKIPPED_BY_CLASS) {
            SKIPPED_BY_CLASS.merge(name, 1, Integer::sum);
        }
    }

    // One memory report line: the five commonest rejected classes, most frequent first; capped because a broken mod produces a long tail
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

    // Called at the start of a resource reload, so each bake is measured on its own
    public static void open() {
        QUADS.open();
        VARIANTS.open();
        FACE_UVS.open();
        MODEL_TEXTURES.open();

        synchronized (SKIPPED_BY_CLASS) {
            SKIPPED_BY_CLASS.clear();
        }
    }

    // Drops the quad pool after a bake; variants stay open since ModelResourceLocations are built all session
    public static void close() {
        QUADS.close();
        FACE_UVS.close();
        MODEL_TEXTURES.close();
        // Variants are not closed with the bake: ModelResourceLocation is constructed all session by item rendering and mods, and the pool is small enough to keep
    }
}
