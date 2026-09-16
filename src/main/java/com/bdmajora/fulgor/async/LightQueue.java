package com.bdmajora.fulgor.async;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

// Insertion-ordered queue of per-chunk light batches for one lane; the main thread (and Async's tick workers) enqueue, one worker dequeues
public final class LightQueue {
    private final Long2ObjectLinkedOpenHashMap<ChunkTasks> tasksByChunk = new Long2ObjectLinkedOpenHashMap<>();
    // A dequeued batch moves here under the same monitor, so nothing observes a gap between dequeue and completion
    private final Long2ObjectOpenHashMap<ChunkTasks> inFlightTasks = new Long2ObjectOpenHashMap<>();
    private final Semaphore workAvailable = new Semaphore(0);
    // Priority lookups must be O(1) per dequeue, so keys are recorded on entry to each class and validated lazily when popped
    private final LongArrayFIFOQueue blockChangeKeys = new LongArrayFIFOQueue();
    private final LongArrayFIFOQueue initialLightKeys = new LongArrayFIFOQueue();
    private int initialLightCount;

    private static boolean changesLightValues(ChunkTasks tasks) {
        if (tasks == null) {
            return false;
        }
        return tasks.initialLightChunk != null
                || tasks.changedSectionSet != null
                || (tasks.changedPositions != null && !tasks.changedPositions.isEmpty());
    }

    private ChunkTasks getOrCreate(long key) {
        ChunkTasks tasks = this.tasksByChunk.get(key);
        if (tasks == null) {
            tasks = new ChunkTasks(key);
            this.tasksByChunk.put(key, tasks);
            AsyncLightStats.CHUNKS_QUEUED.increment();
        }
        return tasks;
    }

    public synchronized void queueBlockChange(int x, int y, int z) {
        long key = ChunkPos.asLong(x >> 4, z >> 4);
        ChunkTasks tasks = this.getOrCreate(key);
        if (tasks.changedPositions == null) {
            tasks.changedPositions = new IntOpenHashSet();
            this.blockChangeKeys.enqueue(key);
        }
        tasks.changedPositions.add((x & 15) | ((z & 15) << 4) | (y << 8));
        this.workAvailable.release(1);
    }

    public synchronized void queueSectionChange(int cx, int sectionY, int cz, boolean empty) {
        if (sectionY < 0 || sectionY > 15) {
            return;
        }
        ChunkTasks tasks = this.getOrCreate(ChunkPos.asLong(cx, cz));
        if (tasks.changedSectionSet == null) {
            tasks.changedSectionSet = new Boolean[16];
        }
        tasks.changedSectionSet[sectionY] = empty;
        this.workAvailable.release(1);
    }

    public synchronized void queueChunkLight(int cx, int cz, Chunk chunk, Boolean[] emptySections, long generation) {
        long key = ChunkPos.asLong(cx, cz);
        ChunkTasks tasks = this.getOrCreate(key);
        if ((tasks.initialLightChunk != null && tasks.initialLightGeneration > generation)
                || tasks.initialLightEdgeGeneration > generation) {
            return;
        }
        if (tasks.initialLightChunk == null) {
            this.initialLightKeys.enqueue(key);
            this.initialLightCount++;
        }
        tasks.initialLightChunk = chunk;
        tasks.initialLightEmptySections = emptySections;
        tasks.initialLightGeneration = generation;
        tasks.relightAttempts = 0;
        // A new propagation pass supersedes a queued edge-finalisation marker; the section set stays as ordinary edge maintenance
        tasks.initialLightEdgeGeneration = 0L;
        tasks.edgeCheckAttempts = 0;
        this.workAvailable.release(1);
    }

    // The cheap load-time init (nibbles and emptiness map, no BFS) for a chunk restored with valid saved light
    public synchronized void queueChunkLoadInit(int cx, int cz, Chunk chunk, Boolean[] emptySections) {
        ChunkTasks tasks = this.getOrCreate(ChunkPos.asLong(cx, cz));
        tasks.loadInitChunk = chunk;
        tasks.loadInitEmptySections = emptySections;
        this.workAvailable.release(1);
    }

    // Re-queues a full relight after a BFS queue overflow; false when a newer generation already occupies the chunk
    public synchronized boolean requeueChunkLight(int cx, int cz, Chunk chunk, Boolean[] emptySections,
                                                  long generation, int previousAttempts) {
        long key = ChunkPos.asLong(cx, cz);
        ChunkTasks tasks = this.getOrCreate(key);
        if ((tasks.initialLightChunk != null && tasks.initialLightGeneration > generation)
                || tasks.initialLightEdgeGeneration > generation) {
            return false;
        }
        if (tasks.initialLightChunk == null) {
            this.initialLightKeys.enqueue(key);
            this.initialLightCount++;
        }
        tasks.initialLightChunk = chunk;
        tasks.initialLightEmptySections = emptySections;
        if (tasks.initialLightGeneration == generation) {
            tasks.relightAttempts = Math.max(tasks.relightAttempts, previousAttempts + 1);
        } else {
            tasks.initialLightGeneration = generation;
            tasks.relightAttempts = previousAttempts + 1;
        }
        tasks.initialLightEdgeGeneration = 0L;
        tasks.edgeCheckAttempts = 0;
        this.workAvailable.release(1);
        return true;
    }

    public synchronized void queueEdgeCheckAllSections(int cx, int cz, boolean isSky) {
        ChunkTasks tasks = this.getOrCreate(ChunkPos.asLong(cx, cz));
        addAllEdgeSections(tasks, isSky);
        this.workAvailable.release(1);
    }

    // The edge-reconciliation phase that gates publishing an initial-light generation; false when newer work already occupies the chunk
    public synchronized boolean queueInitialLightEdgeCheckAllSections(int cx, int cz, boolean isSky,
                                                                      long generation, int attempts) {
        if (generation <= 0L || attempts < 0) {
            throw new IllegalArgumentException("Invalid initial-light edge generation/attempt");
        }
        ChunkTasks tasks = this.getOrCreate(ChunkPos.asLong(cx, cz));
        if ((tasks.initialLightChunk != null && tasks.initialLightGeneration >= generation)
                || tasks.initialLightEdgeGeneration > generation) {
            return false;
        }
        addAllEdgeSections(tasks, isSky);
        if (tasks.initialLightEdgeGeneration == generation) {
            tasks.edgeCheckAttempts = Math.max(tasks.edgeCheckAttempts, attempts);
        } else {
            tasks.initialLightEdgeGeneration = generation;
            tasks.edgeCheckAttempts = attempts;
        }
        this.workAvailable.release(1);
        return true;
    }

    private static void addAllEdgeSections(ChunkTasks tasks, boolean isSky) {
        if (isSky) {
            if (tasks.queuedEdgeChecksSky == null) {
                tasks.queuedEdgeChecksSky = new IntOpenHashSet();
            }
            for (int s = -1; s <= 16; ++s) {
                tasks.queuedEdgeChecksSky.add(s);
            }
        } else {
            if (tasks.queuedEdgeChecksBlock == null) {
                tasks.queuedEdgeChecksBlock = new IntOpenHashSet();
            }
            for (int s = -1; s <= 16; ++s) {
                tasks.queuedEdgeChecksBlock.add(s);
            }
        }
    }

    // First batch carrying an initial light, ahead of edge-only batches
    public synchronized ChunkTasks removeFirstInitialLightTask() {
        while (!this.initialLightKeys.isEmpty()) {
            long key = this.initialLightKeys.dequeueLong();
            ChunkTasks task = this.tasksByChunk.get(key);
            // A stale entry: the batch left the map through another path
            if (task == null || task.initialLightChunk == null) {
                continue;
            }
            this.tasksByChunk.remove(key);
            onTaskDequeued(task);
            return task;
        }
        return null;
    }

    public synchronized ChunkTasks removeFirstTask() {
        if (this.tasksByChunk.isEmpty()) {
            return null;
        }
        long key = this.tasksByChunk.firstLongKey();
        ChunkTasks task = this.tasksByChunk.remove(key);
        onTaskDequeued(task);
        return task;
    }

    // First batch carrying block changes, so a player's placement beats chunk loading
    public synchronized ChunkTasks removeFirstBlockChangeTask() {
        while (!this.blockChangeKeys.isEmpty()) {
            long key = this.blockChangeKeys.dequeueLong();
            ChunkTasks task = this.tasksByChunk.get(key);
            if (task == null || task.changedPositions == null || task.changedPositions.isEmpty()) {
                continue;
            }
            this.tasksByChunk.remove(key);
            onTaskDequeued(task);
            return task;
        }
        return null;
    }

    public synchronized boolean hasInitialLightTask() {
        return this.initialLightCount > 0;
    }

    public void removeChunk(int cx, int cz) {
        ChunkTasks task;
        synchronized (this) {
            task = this.tasksByChunk.remove(ChunkPos.asLong(cx, cz));
            if (task != null) {
                onTaskRemoved(task);
            }
        }
        if (task != null) {
            task.onComplete.set(null);
        }
    }

    // Always called from the worker's finally block
    void completeTask(ChunkTasks task) {
        synchronized (this) {
            if (this.inFlightTasks.get(task.chunkCoordinate) == task) {
                this.inFlightTasks.remove(task.chunkCoordinate);
            }
        }
        task.onComplete.set(null);
    }

    private void onTaskRemoved(ChunkTasks task) {
        if (task.initialLightChunk != null) {
            this.initialLightCount--;
        }
    }

    private void onTaskDequeued(ChunkTasks task) {
        onTaskRemoved(task);
        this.inFlightTasks.put(task.chunkCoordinate, task);
    }

    public synchronized boolean hasPendingWork(int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        return this.tasksByChunk.containsKey(key) || this.inFlightTasks.containsKey(key);
    }

    // Queued or in flight; unlike isEmpty this is the right question for a completion check
    public synchronized boolean hasWork() {
        return !this.tasksByChunk.isEmpty() || !this.inFlightTasks.isEmpty();
    }

    public synchronized boolean isEmpty() {
        return this.tasksByChunk.isEmpty();
    }

    public synchronized int size() {
        return this.tasksByChunk.size();
    }

    // Whether queued work could still change the chunk's light values; edge-only batches refine seams and do not count
    public synchronized boolean hasPendingLightWork(long key) {
        return changesLightValues(this.tasksByChunk.get(key)) || changesLightValues(this.inFlightTasks.get(key));
    }

    // Completion of the current batch for a chunk, or null; callers re-check afterwards since a new batch may have arrived meanwhile
    synchronized Future<Void> getPendingWorkFuture(long key) {
        ChunkTasks inFlight = this.inFlightTasks.get(key);
        if (inFlight != null) {
            return inFlight.onComplete;
        }
        ChunkTasks queued = this.tasksByChunk.get(key);
        return queued == null ? null : queued.onComplete;
    }

    // Blocks until work arrives, then drains the excess permits so one wake processes everything
    void waitForWork() throws InterruptedException {
        this.workAvailable.acquire();
        this.workAvailable.drainPermits();
    }

    void wakeUp() {
        this.workAvailable.release(1);
    }

    // On the client nothing acquires the permits, so they are dropped each drain rather than accumulating
    void clearWorkSignal() {
        this.workAvailable.drainPermits();
    }
}
