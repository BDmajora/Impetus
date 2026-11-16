package com.bdmajora.impetus.engine.impl.render.chunk.map;

import it.unimi.dsi.fastutil.longs.*;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;

// Tracks which chunks have all their data and neighbors and queues load and unload events.
public class ChunkTracker implements ClientChunkEventListener {
    // Flags of every tracked chunk.
    private final Long2IntOpenHashMap chunkStatus = new Long2IntOpenHashMap();
    // Chunks that are ready.
    private final LongOpenHashSet chunkReady = new LongOpenHashSet();

    // Chunks that became unready.
    private final LongSet unloadQueue = new LongOpenHashSet();
    // Chunks that became ready.
    private final LongSet loadQueue = new LongOpenHashSet();

    // Neighbor radius a chunk needs.
    private int requiredNeighborRadius;

    // Creates a tracker with a neighbor radius of one.
    public ChunkTracker() {
        this(1);
    }

    // Creates a tracker and rejects a negative radius.
    public ChunkTracker(int requiredNeighborRadius) {
        if (requiredNeighborRadius < 0) {
            throw new IllegalArgumentException("requiredNeighborRadius must be nonnegative");
        }
        this.requiredNeighborRadius = requiredNeighborRadius;
    }

    // Stores the neighbor radius and rechecks the chunks and rejects a negative radius.
    public void setRequiredNeighborRadius(int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be nonnegative");
        }
        if (this.requiredNeighborRadius == radius) {
            return;
        }
        boolean fullUpdate = radius > this.requiredNeighborRadius;
        if (fullUpdate) {
            // Clears the ready chunks for a stricter radius
            var readyIterator = this.chunkReady.iterator();
            while (readyIterator.hasNext()) {
                long key = readyIterator.nextLong();
                if (!this.loadQueue.remove(key)) {
                    this.unloadQueue.add(key);
                }
            }
            this.chunkReady.clear();
        }
        this.requiredNeighborRadius = radius;
        // Rechecks every tracked chunk
        var trackedChunksIterator = this.chunkStatus.keySet().iterator();
        while (trackedChunksIterator.hasNext()) {
            long pos = trackedChunksIterator.nextLong();
            if (!fullUpdate && this.chunkReady.contains(pos)) {
                continue;
            }
            var x = PositionUtil.unpackChunkX(pos);
            var z = PositionUtil.unpackChunkZ(pos);
            this.updateMerged(x, z);
        }
    }

    // Ignores the new center.
    @Override
    public void updateMapCenter(int chunkX, int chunkZ) {

    }

    // Ignores the new load distance.
    @Override
    public void updateLoadDistance(int loadDistance) {

    }

    // Adds flags to a chunk and rechecks its neighbors.
    @Override
    public void onChunkStatusAdded(int x, int z, int flags) {
        var key = PositionUtil.packChunk(x, z);

        var prev = this.chunkStatus.get(key);
        var cur = prev | flags;

        if (prev == cur) {
            return;
        }

        this.chunkStatus.put(key, cur);

        this.updateNeighbors(x, z);
    }

    // Removes flags from a chunk and rechecks its neighbors.
    @Override
    public void onChunkStatusRemoved(int x, int z, int flags) {
        var key = PositionUtil.packChunk(x, z);

        var prev = this.chunkStatus.get(key);
        int cur = prev & ~flags;

        if (prev == cur) {
            return;
        }

        if (cur == this.chunkStatus.defaultReturnValue()) {
            this.chunkStatus.remove(key);
        } else {
            this.chunkStatus.put(key, cur);
        }

        this.updateNeighbors(x, z);
    }

    // Rechecks every chunk within the radius.
    private void updateNeighbors(int x, int z) {
        int r = this.requiredNeighborRadius;
        for (int ox = -r; ox <= r; ox++) {
            for (int oz = -r; oz <= r; oz++) {
                this.updateMerged(ox + x, oz + z);
            }
        }
    }

    // Rechecks one chunk and queues a load or unload event.
    private void updateMerged(int x, int z) {
        long key = PositionUtil.packChunk(x, z);

        int r = this.requiredNeighborRadius;
        int flags = this.chunkStatus.get(key);

        for (int ox = -r; ox <= r; ox++) {
            for (int oz = -r; oz <= r; oz++) {
                flags &= this.chunkStatus.get(PositionUtil.packChunk(ox + x, oz + z));
            }
        }

        if (flags == ChunkStatus.FLAG_ALL) {
            if (this.chunkReady.add(key) && !this.unloadQueue.remove(key)) {
                this.loadQueue.add(key);
            }
        } else {
            if (this.chunkReady.remove(key) && !this.loadQueue.remove(key)) {
                this.unloadQueue.add(key);
            }
        }
    }

    // Returns a read only view of the ready chunks.
    public LongCollection getReadyChunks() {
        return LongSets.unmodifiable(this.chunkReady);
    }

    // Passes the queued unload and load events to the handlers and clears them.
    public void forEachEvent(ChunkEventHandler loadEventHandler, ChunkEventHandler unloadEventHandler) {
        forEachChunk(this.unloadQueue, unloadEventHandler);
        this.unloadQueue.clear();

        forEachChunk(this.loadQueue, loadEventHandler);
        this.loadQueue.clear();
    }

    // Calls a handler for every chunk of a queue.
    public static void forEachChunk(LongCollection queue, ChunkEventHandler handler) {
        var iterator = queue.iterator();

        while (iterator.hasNext()) {
            var pos = iterator.nextLong();

            var x = PositionUtil.unpackChunkX(pos);
            var z = PositionUtil.unpackChunkZ(pos);

            handler.apply(x, z);
        }
    }

    // Handler of a chunk event.
    public interface ChunkEventHandler {
        // Handles a chunk.
        void apply(int x, int z);
    }
}