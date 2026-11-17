package com.bdmajora.fulgor.async;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

// Queue of light updates by chunk.
public final class LightQueue {
    // Pending tasks by chunk in queue order.
    private final Long2ObjectLinkedOpenHashMap<ChunkTasks> tasksByChunk = new Long2ObjectLinkedOpenHashMap<>();
    // Tasks being run by chunk.
    private final Long2ObjectOpenHashMap<ChunkTasks> inFlightTasks = new Long2ObjectOpenHashMap<>();
    // Signal of available work.
    private final Semaphore workAvailable = new Semaphore(0);
    // Chunks with block changes in order.
    private final LongArrayFIFOQueue blockChangeKeys = new LongArrayFIFOQueue();
    // Chunks with initial lighting in order.
    private final LongArrayFIFOQueue initialLightKeys = new LongArrayFIFOQueue();
    // Number of queued initial lightings.
    private int initialLightCount;

    // Returns whether a task can change light values.
    private static boolean changesLightValues(ChunkTasks tasks) {
        if (tasks == null) {
            return false;
        }
        return tasks.initialLightChunk != null
                || tasks.changedSectionSet != null
                || (tasks.changedPositions != null && !tasks.changedPositions.isEmpty());
    }

    // Returns the tasks of a chunk and creates them when missing.
    private ChunkTasks getOrCreate(long key) {
        ChunkTasks tasks = this.tasksByChunk.get(key);
        if (tasks == null) {
            tasks = new ChunkTasks(key);
            this.tasksByChunk.put(key, tasks);
            AsyncLightStats.CHUNKS_QUEUED.increment();
        }
        return tasks;
    }

    // Queues the block change.
    public synchronized void queueBlockChange(int x, int y, int z) {
        if (y < 0 || y > 255) {
            return;
        }
        long key = ChunkPos.asLong(x >> 4, z >> 4);
        ChunkTasks tasks = this.getOrCreate(key);
        if (tasks.changedPositions == null) {
            tasks.changedPositions = new IntOpenHashSet();
            this.blockChangeKeys.enqueue(key);
        }
        tasks.changedPositions.add((x & 15) | ((z & 15) << 4) | (y << 8));
        this.workAvailable.release(1);
    }

    // Queues the section change.
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

    // Queues the chunk light.
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
        tasks.initialLightEdgeGeneration = 0L;
        tasks.edgeCheckAttempts = 0;
        this.workAvailable.release(1);
    }

    // Queues the chunk load init.
    public synchronized void queueChunkLoadInit(int cx, int cz, Chunk chunk, Boolean[] emptySections) {
        ChunkTasks tasks = this.getOrCreate(ChunkPos.asLong(cx, cz));
        tasks.loadInitChunk = chunk;
        tasks.loadInitEmptySections = emptySections;
        this.workAvailable.release(1);
    }

    // Requeues the chunk light.
    public synchronized boolean requeueChunkLight(int cx, int cz, Chunk chunk, Boolean[] emptySections,
                                                  // Generation of the queue when the task was made.
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

    // Queues edge checks of every section of a chunk.
    public synchronized void queueEdgeCheckAllSections(int cx, int cz, boolean isSky) {
        ChunkTasks tasks = this.getOrCreate(ChunkPos.asLong(cx, cz));
        addAllEdgeSections(tasks, isSky);
        this.workAvailable.release(1);
    }

    // Queues initial edge checks of every section of a chunk.
    public synchronized boolean queueInitialLightEdgeCheckAllSections(int cx, int cz, boolean isSky,
                                                                      // Generation of the queue when the task was made.
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

    // Adds every section to the edge checks of a task.
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

    // Removes the first initial light task.
    public synchronized ChunkTasks removeFirstInitialLightTask() {
        while (!this.initialLightKeys.isEmpty()) {
            long key = this.initialLightKeys.dequeueLong();
            ChunkTasks task = this.tasksByChunk.get(key);
            if (task == null || task.initialLightChunk == null) {
                continue;
            }
            this.tasksByChunk.remove(key);
            onTaskDequeued(task);
            return task;
        }
        return null;
    }

    // Removes the first task.
    public synchronized ChunkTasks removeFirstTask() {
        if (this.tasksByChunk.isEmpty()) {
            return null;
        }
        long key = this.tasksByChunk.firstLongKey();
        ChunkTasks task = this.tasksByChunk.remove(key);
        onTaskDequeued(task);
        return task;
    }

    // Removes the first block change task.
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

    // Returns whether an initial light task is queued.
    public synchronized boolean hasInitialLightTask() {
        return this.initialLightCount > 0;
    }

    // Removes the chunk.
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

    // Marks a task done and completes its future.
    void completeTask(ChunkTasks task) {
        synchronized (this) {
            if (this.inFlightTasks.get(task.chunkCoordinate) == task) {
                this.inFlightTasks.remove(task.chunkCoordinate);
            }
        }
        task.onComplete.set(null);
    }

    // Forgets a removed task's keys and completes its future.
    private void onTaskRemoved(ChunkTasks task) {
        if (task.initialLightChunk != null) {
            this.initialLightCount--;
        }
    }

    // Moves a dequeued task to the in-flight set.
    private void onTaskDequeued(ChunkTasks task) {
        onTaskRemoved(task);
        this.inFlightTasks.put(task.chunkCoordinate, task);
    }

    // Returns whether a chunk has queued or in-flight work.
    public synchronized boolean hasPendingWork(int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        return this.tasksByChunk.containsKey(key) || this.inFlightTasks.containsKey(key);
    }

    // Returns whether any work is queued or in flight.
    public synchronized boolean hasWork() {
        return !this.tasksByChunk.isEmpty() || !this.inFlightTasks.isEmpty();
    }

    // Returns whether no task is queued.
    public synchronized boolean isEmpty() {
        return this.tasksByChunk.isEmpty();
    }

    // Returns the size.
    public synchronized int size() {
        return this.tasksByChunk.size();
    }

    // Returns whether a chunk has light value work queued or in flight.
    public synchronized boolean hasPendingLightWork(long key) {
        return changesLightValues(this.tasksByChunk.get(key)) || changesLightValues(this.inFlightTasks.get(key));
    }

    // Returns the pending work future.
    synchronized Future<Void> getPendingWorkFuture(long key) {
        ChunkTasks inFlight = this.inFlightTasks.get(key);
        if (inFlight != null) {
            return inFlight.onComplete;
        }
        ChunkTasks queued = this.tasksByChunk.get(key);
        return queued == null ? null : queued.onComplete;
    }

    // Blocks until work is signalled.
    void waitForWork() throws InterruptedException {
        this.workAvailable.acquire();
        this.workAvailable.drainPermits();
    }

    // Signals the worker.
    void wakeUp() {
        this.workAvailable.release(1);
    }

    // Clears the work signal.
    void clearWorkSignal() {
        this.workAvailable.drainPermits();
    }
}
