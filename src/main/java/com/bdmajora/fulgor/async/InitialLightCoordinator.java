package com.bdmajora.fulgor.async;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.async.engine.BfsLightEngine;
import com.google.common.util.concurrent.SettableFuture;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.concurrent.Future;

// Coordinates the two phases of a chunk's initial lighting across the sky and block lanes: usable once both have propagated, published as light-ready only once both have also reconciled its edges for the same generation
final class InitialLightCoordinator {
    static final int MAX_RELIGHT_ATTEMPTS = 2;

    static final int LANE_SKY = 1;
    static final int LANE_BLOCK = 1 << 1;

    private final LoadedChunkMap loadedChunks;
    private final LightQueue skyQueue;
    private final LightQueue blockQueue;

    private final Object lock = new Object();
    private final Long2ObjectOpenHashMap<Completion> completions = new Long2ObjectOpenHashMap<>();
    private long nextGeneration;

    InitialLightCoordinator(LoadedChunkMap loadedChunks, LightQueue skyQueue, LightQueue blockQueue) {
        this.loadedChunks = loadedChunks;
        this.skyQueue = skyQueue;
        this.blockQueue = blockQueue;
    }

    Completion queue(int chunkX, int chunkZ, Chunk chunk, Boolean[] emptySections) {
        return queue(chunkX, chunkZ, chunk, emptySections, 0, false);
    }

    // Starts a replacement generation after an overflow, chaining the old completion to the new one so existing waiters see its result
    Completion queueRecovery(int chunkX, int chunkZ, Chunk chunk, Boolean[] emptySections, int edgeRecoveryAttempts) {
        return queue(chunkX, chunkZ, chunk, emptySections, edgeRecoveryAttempts, true);
    }

    private Completion queue(int chunkX, int chunkZ, Chunk chunk, Boolean[] emptySections,
                             int edgeRecoveryAttempts, boolean handoffSupersededCompletion) {
        if (edgeRecoveryAttempts < 0 || edgeRecoveryAttempts > MAX_RELIGHT_ATTEMPTS) {
            throw new IllegalArgumentException("Invalid edge-recovery attempt: " + edgeRecoveryAttempts);
        }

        long key = ChunkPos.asLong(chunkX, chunkZ);
        Completion completion;
        Completion superseded;

        synchronized (this.lock) {
            long generation = ++this.nextGeneration;
            int requiredLanes = (this.skyQueue != null ? LANE_SKY : 0) | (this.blockQueue != null ? LANE_BLOCK : 0);
            completion = new Completion(generation, requiredLanes, chunk, edgeRecoveryAttempts);
            superseded = this.completions.put(key, completion);

            // Both lanes are queued under the generation lock so a newer request can never be followed by an older insertion
            AsyncLitChunk lit = (AsyncLitChunk) chunk;
            lit.fulgor$setLightReady(false);
            lit.fulgor$setLightUsable(false);
            if (this.skyQueue != null) {
                this.skyQueue.queueChunkLight(chunkX, chunkZ, chunk, emptySections, generation);
            }
            if (this.blockQueue != null) {
                this.blockQueue.queueChunkLight(chunkX, chunkZ, chunk, emptySections, generation);
            }
        }

        if (superseded != null) {
            if (handoffSupersededCompletion) {
                completion.future.addListener(() -> superseded.finish(completion.published), Runnable::run);
            } else {
                superseded.finish(false);
            }
        }
        return completion;
    }

    void removeChunk(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        Completion removed;
        synchronized (this.lock) {
            removed = this.completions.remove(key);
            if (this.skyQueue != null) {
                this.skyQueue.removeChunk(chunkX, chunkZ);
            }
            if (this.blockQueue != null) {
                this.blockQueue.removeChunk(chunkX, chunkZ);
            }
        }
        if (removed != null) {
            removed.finish(false);
        }
    }

    // Restarts coordinated lighting when the final edge pass overflowed; an edge-only retry cannot recover updates dropped away from the seam
    boolean restartAfterEdgeOverflow(ChunkTasks task, int chunkX, int chunkZ, String laneName) {
        if (task.initialLightEdgeGeneration <= 0L) {
            return false;
        }
        if (task.edgeCheckAttempts >= MAX_RELIGHT_ATTEMPTS) {
            Fulgor.LOGGER.error("{} lane: chunk ({}, {}) overflowed final edge reconciliation {} times, giving up",
                    laneName, chunkX, chunkZ, task.edgeCheckAttempts + 1);
            return false;
        }

        synchronized (this.lock) {
            Completion completion = this.completions.get(task.chunkCoordinate);
            // A newer generation already superseded this edge pass
            if (completion == null || completion.generation != task.initialLightEdgeGeneration) {
                return true;
            }
            if (this.loadedChunks.get(task.chunkCoordinate) != completion.chunk) {
                return false;
            }
            queueRecovery(chunkX, chunkZ, completion.chunk, BfsLightEngine.getEmptySectionsForChunk(completion.chunk),
                    task.edgeCheckAttempts + 1);
            return true;
        }
    }

    // Records one lane's initial propagation; once every lane is in, the chunk becomes usable and the generation-tagged edge passes are queued
    void completeInitial(ChunkTasks task, int lane) {
        if (task.initialLightGeneration <= 0L) {
            return;
        }

        long chunkCoordinate = task.chunkCoordinate;
        Completion failedCompletion = null;
        Throwable transitionFailure = null;

        synchronized (this.lock) {
            Completion completion = this.completions.get(chunkCoordinate);
            if (completion == null) {
                return;
            }
            if (completion.completeInitial(task.initialLightGeneration, lane) != Result.INITIAL_COMPLETE) {
                return;
            }

            AsyncLitChunk lit = (AsyncLitChunk) completion.chunk;
            if (this.loadedChunks.get(chunkCoordinate) != completion.chunk) {
                this.completions.remove(chunkCoordinate);
                lit.fulgor$setLightReady(false);
                lit.fulgor$setLightUsable(false);
                failedCompletion = completion;
            } else {
                try {
                    lit.fulgor$setLightUsable(true);
                    int chunkX = (int) chunkCoordinate;
                    int chunkZ = (int) (chunkCoordinate >> 32);
                    boolean skyQueued = this.skyQueue == null || this.skyQueue.queueInitialLightEdgeCheckAllSections(
                            chunkX, chunkZ, true, completion.generation, completion.edgeRecoveryAttempts);
                    boolean blockQueued = this.blockQueue == null || this.blockQueue.queueInitialLightEdgeCheckAllSections(
                            chunkX, chunkZ, false, completion.generation, completion.edgeRecoveryAttempts);
                    if (!skyQueued || !blockQueued) {
                        throw new IllegalStateException("A newer light task occupied the edge queue");
                    }
                } catch (Throwable t) {
                    this.completions.remove(chunkCoordinate);
                    lit.fulgor$setLightReady(false);
                    lit.fulgor$setLightUsable(false);
                    failedCompletion = completion;
                    transitionFailure = t;
                }
            }
        }

        if (failedCompletion != null) {
            failedCompletion.finish(false);
        }
        if (transitionFailure != null) {
            Fulgor.LOGGER.error("Failed to start edge reconciliation for chunk ({}, {})",
                    (int) chunkCoordinate, (int) (chunkCoordinate >> 32), transitionFailure);
        }
    }

    // Records one lane's generation-tagged edge pass; the last lane copies the reconciled light into vanilla storage and publishes the chunk
    void completeEdges(ChunkTasks task, int lane) {
        if (task.initialLightEdgeGeneration <= 0L) {
            return;
        }

        long chunkCoordinate = task.chunkCoordinate;
        Completion completion;

        synchronized (this.lock) {
            completion = this.completions.get(chunkCoordinate);
            if (completion == null) {
                return;
            }
            if (completion.completeEdges(task.initialLightEdgeGeneration, lane) != Result.COMPLETE) {
                return;
            }
        }

        boolean published = false;
        Throwable publishFailure = null;
        AsyncLitChunk lit = (AsyncLitChunk) completion.chunk;
        // Publication is serialised only against another generation of the same chunk; the generation checks around the copy stop a stale one landing
        synchronized (completion.chunk) {
            boolean currentGeneration;
            synchronized (this.lock) {
                currentGeneration = this.completions.get(chunkCoordinate) == completion
                        && this.loadedChunks.get(chunkCoordinate) == completion.chunk;
            }

            try {
                if (currentGeneration) {
                    lit.fulgor$syncLightToVanilla();
                }
            } catch (Throwable t) {
                publishFailure = t;
            }

            synchronized (this.lock) {
                if (this.completions.get(chunkCoordinate) == completion) {
                    this.completions.remove(chunkCoordinate);
                    currentGeneration = this.loadedChunks.get(chunkCoordinate) == completion.chunk;
                    if (currentGeneration && publishFailure == null) {
                        lit.fulgor$setLightReady(true);
                        published = true;
                    } else if (currentGeneration) {
                        lit.fulgor$setLightReady(false);
                        lit.fulgor$setLightUsable(false);
                    }
                }
            }
        }

        completion.finish(published);
        if (publishFailure != null) {
            Fulgor.LOGGER.error("Failed to publish edge-reconciled light for chunk ({}, {})",
                    (int) chunkCoordinate, (int) (chunkCoordinate >> 32), publishFailure);
        }
    }

    boolean hasPending(long chunkCoordinate) {
        synchronized (this.lock) {
            return this.completions.containsKey(chunkCoordinate);
        }
    }

    Future<Void> getPendingFuture(long chunkCoordinate) {
        synchronized (this.lock) {
            Completion completion = this.completions.get(chunkCoordinate);
            return completion == null ? null : completion.future;
        }
    }

    enum Result {
        IGNORED,
        WAITING,
        INITIAL_COMPLETE,
        COMPLETE
    }

    // One generation of a chunk's initial lighting, with the exactly-once lane bookkeeping folded in
    static final class Completion {
        final long generation;
        final int edgeRecoveryAttempts;
        final int requiredLanes;
        final SettableFuture<Void> future = SettableFuture.create();
        final Chunk chunk;
        volatile boolean published;
        private int initialCompletedLanes;
        private int edgeCompletedLanes;

        Completion(long generation, int requiredLanes, Chunk chunk, int edgeRecoveryAttempts) {
            this.generation = generation;
            this.requiredLanes = requiredLanes;
            this.chunk = chunk;
            this.edgeRecoveryAttempts = edgeRecoveryAttempts;
        }

        synchronized Result completeInitial(long taskGeneration, int lane) {
            if (taskGeneration != this.generation || (lane & this.requiredLanes) == 0 || (this.initialCompletedLanes & lane) != 0) {
                return Result.IGNORED;
            }
            this.initialCompletedLanes |= lane;
            return this.initialCompletedLanes == this.requiredLanes ? Result.INITIAL_COMPLETE : Result.WAITING;
        }

        synchronized Result completeEdges(long taskGeneration, int lane) {
            if (taskGeneration != this.generation || (lane & this.requiredLanes) == 0
                    || this.initialCompletedLanes != this.requiredLanes || (this.edgeCompletedLanes & lane) != 0) {
                return Result.IGNORED;
            }
            this.edgeCompletedLanes |= lane;
            return this.edgeCompletedLanes == this.requiredLanes ? Result.COMPLETE : Result.WAITING;
        }

        synchronized void finish(boolean published) {
            if (this.future.isDone()) {
                return;
            }
            this.published = published;
            this.future.set(null);
        }
    }
}
