package com.bdmajora.impetus.engine.impl.render.chunk.map;

// World that carries a chunk tracker.
public interface ChunkTrackerHolder {
    // Returns the tracker of a world.
    static ChunkTracker get(Object world) {
        return ((ChunkTrackerHolder) world).impetus$getTracker();
    }

    // Returns the tracker.
    ChunkTracker impetus$getTracker();
}
