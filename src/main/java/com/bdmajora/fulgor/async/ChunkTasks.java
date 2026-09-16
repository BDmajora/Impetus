package com.bdmajora.fulgor.async;

import com.google.common.util.concurrent.SettableFuture;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.world.chunk.Chunk;

// One chunk's batched light work: block and section changes, an initial lighting request and edge checks accumulate here until a worker takes the batch
public final class ChunkTasks {
    public final long chunkCoordinate;
    // Completed when the batch has been processed or dropped, for unload to wait on
    public final SettableFuture<Void> onComplete;
    // Changed positions packed as x | z << 4 | y << 8
    public IntOpenHashSet changedPositions;
    // Per-section emptiness changes: null untouched, TRUE emptied, FALSE filled
    public Boolean[] changedSectionSet;
    // Non-null when the chunk needs its initial lighting pass
    public Chunk initialLightChunk;
    public Boolean[] initialLightEmptySections;
    // Generation of the coordinated relight this batch carries, zero for an ordinary batch
    public long initialLightGeneration;
    // Non-null for a chunk restored with valid saved light, which only needs its nibbles and emptiness map set up
    public Chunk loadInitChunk;
    public Boolean[] loadInitEmptySections;
    public IntOpenHashSet queuedEdgeChecksSky;
    public IntOpenHashSet queuedEdgeChecksBlock;
    // Generation whose final edge reconciliation this batch carries, zero for ordinary edge maintenance
    public long initialLightEdgeGeneration;
    public int edgeCheckAttempts;
    public int relightAttempts;

    public ChunkTasks(long chunkCoordinate) {
        this.chunkCoordinate = chunkCoordinate;
        this.onComplete = SettableFuture.create();
    }
}
