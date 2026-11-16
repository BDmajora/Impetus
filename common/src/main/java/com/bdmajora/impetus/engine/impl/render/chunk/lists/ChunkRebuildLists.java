package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import com.bdmajora.impetus.engine.impl.render.chunk.ChunkUpdateType;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;

// Sections that need a rebuild grouped by update type.
public record ChunkRebuildLists(Map<ChunkUpdateType, ArrayDeque<RenderSection>> byUpdateType, boolean hasAdditionalUpdates, Map<ChunkUpdateType, Integer> queueOverflowCounts) {
    // Rejects null maps.
    public ChunkRebuildLists {
        java.util.Objects.requireNonNull(byUpdateType, "byUpdateType");
        java.util.Objects.requireNonNull(queueOverflowCounts, "queueOverflowCounts");
    }

    // Returns the number of queued and dropped sections of a type.
    public int getUpdateCount(ChunkUpdateType type) {
        ArrayDeque<RenderSection> queue = byUpdateType.get(type);
        long total = (queue == null ? 0 : queue.size()) + (long) queueOverflowCounts.getOrDefault(type, 0);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    // Returns whether nothing is queued.
    public boolean isEmpty() {
        if (hasAdditionalUpdates) {
            return false;
        }
        for (var queue : byUpdateType.values()) {
            if (!queue.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // Creates empty queues for every type.
    public static ChunkRebuildLists empty() {
        Map<ChunkUpdateType, ArrayDeque<RenderSection>> rebuildLists = new EnumMap<>(ChunkUpdateType.class);

        for (var type : ChunkUpdateType.VALUES) {
            rebuildLists.put(type, new ArrayDeque<>());
        }

        return new ChunkRebuildLists(rebuildLists, false, new EnumMap<>(ChunkUpdateType.class));
    }
}
