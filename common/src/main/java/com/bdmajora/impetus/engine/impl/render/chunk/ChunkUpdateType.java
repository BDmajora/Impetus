package com.bdmajora.impetus.engine.impl.render.chunk;

// the type of chunk update task
public enum ChunkUpdateType {
    // First-time build; the queue size is arbitrary but keeps a worker pool saturated during world load while bounding the per-frame snapshot burst
    INITIAL_BUILD,
    // chunk geometry is being sorted because the camera position changed
    SORT,
    // Like SORT, but blocks the main thread if the camera is near enough that the sort must be reflected quickly
    IMPORTANT_SORT,
    // chunk data has changed and remeshing is required
    REBUILD,
    // Like REBUILD, but blocks the main thread if the camera is near enough that the rebuild must be seen quickly
    IMPORTANT_REBUILD;

    // values() clones per call, and the collector asks every frame
    public static final ChunkUpdateType[] VALUES = values();

    // borrowed from PR #2016
    public static ChunkUpdateType getPromotionUpdateType(ChunkUpdateType prev, ChunkUpdateType next) {
        // No point submitting the same update twice
        if (prev == next) {
            return null;
        }

        if (prev == null || prev == SORT) {
            return next;
        }
        if (next == IMPORTANT_REBUILD
                || (prev == IMPORTANT_SORT && next == REBUILD)
                || (prev == REBUILD && next == IMPORTANT_SORT)) {
            return IMPORTANT_REBUILD;
        }
        return null;
    }

    // true if the task is "important" and should block the main thread when the camera is near enough
    public boolean isImportant() {
        return this == IMPORTANT_REBUILD || this == IMPORTANT_SORT;
    }

    // true if the task only sorts, rather than performing a full chunk rebuild
    public boolean isSort() {
        return this == SORT || this == IMPORTANT_SORT;
    }
}
