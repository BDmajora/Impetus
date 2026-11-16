package com.bdmajora.impetus.engine.impl.render.chunk;

// Kinds of chunk update tasks.
public enum ChunkUpdateType {
    // First build of a section.
    INITIAL_BUILD,
    // Sort after the camera moved.
    SORT,
    // Sort that blocks the main thread.
    IMPORTANT_SORT,
    // Rebuild after the data changed.
    REBUILD,
    // Rebuild that blocks the main thread.
    IMPORTANT_REBUILD;

    // Every kind.
    public static final ChunkUpdateType[] VALUES = values();

    // Returns whether a queued update can be replaced by another.
    @Deprecated
    public static boolean canPromote(ChunkUpdateType prev, ChunkUpdateType next) {
        return prev == null || (prev == REBUILD && next == IMPORTANT_REBUILD);
    }

    // Returns the update to queue when another update is already queued or null for none.
    public static ChunkUpdateType getPromotionUpdateType(ChunkUpdateType prev, ChunkUpdateType next) {
        // Skips a repeated update
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

    // Returns whether the task is important.
    public boolean isImportant() {
        return this == IMPORTANT_REBUILD || this == IMPORTANT_SORT;
    }

    // Returns whether the task only sorts.
    public boolean isSort() {
        return this == SORT || this == IMPORTANT_SORT;
    }

    static {
        if (VALUES.length > 7) {
            throw new AssertionError("The occlusion system currently assumes there are at most 7 update types");
        }
    }
}
