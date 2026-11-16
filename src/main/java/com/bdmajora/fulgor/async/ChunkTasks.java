package com.bdmajora.fulgor.async;

import com.google.common.util.concurrent.SettableFuture;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.world.chunk.Chunk;

// Pending light work of one chunk.
public final class ChunkTasks {
    // Packed chunk position.
    public final long chunkCoordinate;
    // Future completed when the work is done.
    public final SettableFuture<Void> onComplete;
    // Packed positions of changed blocks.
    public IntOpenHashSet changedPositions;
    // Changed section set.
    public Boolean[] changedSectionSet;
    // Chunk to light from scratch or null.
    public Chunk initialLightChunk;
    // Empty flags of its sections.
    public Boolean[] initialLightEmptySections;
    // Queue generation of the initial lighting.
    public long initialLightGeneration;
    // Chunk to seed on load or null.
    public Chunk loadInitChunk;
    // Empty flags of its sections.
    public Boolean[] loadInitEmptySections;
    // Sections whose sky light edges need checks.
    public IntOpenHashSet queuedEdgeChecksSky;
    // Sections whose block light edges need checks.
    public IntOpenHashSet queuedEdgeChecksBlock;
    // Queue generation of the edge checks.
    public long initialLightEdgeGeneration;
    // Number of edge check attempts.
    public int edgeCheckAttempts;
    // Number of relight attempts.
    public int relightAttempts;

    // Creates a chunk tasks from its chunk coordinate.
    public ChunkTasks(long chunkCoordinate) {
        this.chunkCoordinate = chunkCoordinate;
        this.onComplete = SettableFuture.create();
    }
}
