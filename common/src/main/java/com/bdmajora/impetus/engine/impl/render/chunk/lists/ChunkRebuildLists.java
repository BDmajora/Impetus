package com.bdmajora.impetus.engine.impl.render.chunk.lists;

import com.bdmajora.impetus.engine.impl.render.chunk.ChunkUpdateType;
import com.bdmajora.impetus.engine.impl.render.chunk.RenderSection;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;

// Sections needing a rebuild bucketed by urgency; hasAdditionalUpdates means the graph walk's per-frame cap dropped some (counted per type in queueOverflowCounts for the debug overlay)
public record ChunkRebuildLists(Map<ChunkUpdateType, ArrayDeque<RenderSection>> byUpdateType, boolean hasAdditionalUpdates, Map<ChunkUpdateType, Integer> queueOverflowCounts) {
    public static final ChunkRebuildLists EMPTY;

    // Sections queued for one update type
    public int getUpdateCount(ChunkUpdateType type) {
        return byUpdateType.get(type).size() + queueOverflowCounts.getOrDefault(type, 0);
    }

    // Nothing queued
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

    static {
        Map<ChunkUpdateType, ArrayDeque<RenderSection>> rebuildLists = new EnumMap<>(ChunkUpdateType.class);

        for (var type : ChunkUpdateType.VALUES) {
            rebuildLists.put(type, new ArrayDeque<>());
        }

        EMPTY = new ChunkRebuildLists(rebuildLists, false, new EnumMap<>(ChunkUpdateType.class));
    }
}
