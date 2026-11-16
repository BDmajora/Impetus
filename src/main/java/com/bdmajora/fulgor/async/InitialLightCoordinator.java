package com.bdmajora.fulgor.async;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.async.engine.BfsLightEngine;
import com.google.common.util.concurrent.SettableFuture;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;

import java.util.concurrent.Future;

// Tracks the first light of each chunk across the sky and block lanes until it is published.
final class InitialLightCoordinator {
    // Largest number of edge recovery attempts of a chunk.
    static final int MAX_RELIGHT_ATTEMPTS = 2;

    // Bit of the sky lane.
    static final int LANE_SKY = 1;
    // Bit of the block lane.
    static final int LANE_BLOCK = 1 << 1;

    // Chunks the world holds now.
    private final LoadedChunkMap loadedChunks;
    // Queue of the sky lane or null.
    private final LightQueue skyQueue;
    // Queue of the block lane or null.
    private final LightQueue blockQueue;

    // Lock of the completion table.
    private final Object lock = new Object();
    // Pending completions by chunk coordinate.
    private final Long2ObjectOpenHashMap<Completion> completions = new Long2ObjectOpenHashMap<>();
    // Last generation handed out.
    private long nextGeneration;

    // Creates a coordinator over the loaded chunks and the lane queues.
    InitialLightCoordinator(LoadedChunkMap loadedChunks, LightQueue skyQueue, LightQueue blockQueue) {
        this.loadedChunks = loadedChunks;
        this.skyQueue = skyQueue;
        this.blockQueue = blockQueue;
    }

    // Queues the first light of a chunk and finishes a superseded completion as unpublished.
    Completion queue(int chunkX, int chunkZ, Chunk chunk, Boolean[] emptySections) {
        return queue(chunkX, chunkZ, chunk, emptySections, 0, false);
    }

    // Queues a relight of a chunk that hands its result to the superseded completion.
    Completion queueRecovery(int chunkX, int chunkZ, Chunk chunk, Boolean[] emptySections, int edgeRecoveryAttempts) {
        return queue(chunkX, chunkZ, chunk, emptySections, edgeRecoveryAttempts, true);
    }

    // Queues the light of a chunk in every lane under a new generation and rejects an attempt count out of range.
    private Completion queue(int chunkX, int chunkZ, Chunk chunk, Boolean[] emptySections,
                             int edgeRecoveryAttempts, boolean handoffSupersededCompletion) {
        if (edgeRecoveryAttempts < 0 || edgeRecoveryAttempts > MAX_RELIGHT_ATTEMPTS) {
            throw new IllegalArgumentException("Invalid edge recovery attempt: " + edgeRecoveryAttempts);
        }

        long key = ChunkPos.asLong(chunkX, chunkZ);
        Completion completion;
        Completion superseded;

        synchronized (this.lock) {
            long generation = ++this.nextGeneration;
            int requiredLanes = (this.skyQueue != null ? LANE_SKY : 0) | (this.blockQueue != null ? LANE_BLOCK : 0);
            completion = new Completion(generation, requiredLanes, chunk, edgeRecoveryAttempts);
            superseded = this.completions.put(key, completion);

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

    // Forgets a chunk in every lane and finishes its completion as unpublished.
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

    // Requeues a chunk whose edge check overflowed and returns whether the task should stop.
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

    // Records the first pass of a lane and starts the edge checks once every lane finished.
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

    // Records the edge pass of a lane and publishes the light once every lane finished.
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

    // Returns whether a chunk has a pending completion.
    boolean hasPending(long chunkCoordinate) {
        synchronized (this.lock) {
            return this.completions.containsKey(chunkCoordinate);
        }
    }

    // Returns the future of the pending completion of a chunk or null.
    Future<Void> getPendingFuture(long chunkCoordinate) {
        synchronized (this.lock) {
            Completion completion = this.completions.get(chunkCoordinate);
            return completion == null ? null : completion.future;
        }
    }

    // Outcomes of recording a lane.
    enum Result {
        // Lane result of an old generation or a repeated lane.
        IGNORED,
        // Lane recorded while other lanes are missing.
        WAITING,
        // Every lane finished the first pass.
        INITIAL_COMPLETE,
        // Every lane finished the edge pass.
        COMPLETE
    }

    // Progress of one generation of the light of a chunk.
    static final class Completion {
        // Generation of the completion.
        final long generation;
        // Number of edge recovery attempts so far.
        final int edgeRecoveryAttempts;
        // Bits of the lanes that must finish.
        final int requiredLanes;
        // Future that finishes with the completion.
        final SettableFuture<Void> future = SettableFuture.create();
        // Chunk being lit.
        final Chunk chunk;
        // Published state of the light.
        volatile boolean published;
        // Bits of the lanes that finished the first pass.
        private int initialCompletedLanes;
        // Bits of the lanes that finished the edge pass.
        private int edgeCompletedLanes;

        // Creates a completion.
        Completion(long generation, int requiredLanes, Chunk chunk, int edgeRecoveryAttempts) {
            this.generation = generation;
            this.requiredLanes = requiredLanes;
            this.chunk = chunk;
            this.edgeRecoveryAttempts = edgeRecoveryAttempts;
        }

        // Records the first pass of a lane and returns the outcome.
        synchronized Result completeInitial(long taskGeneration, int lane) {
            if (taskGeneration != this.generation || (lane & this.requiredLanes) == 0 || (this.initialCompletedLanes & lane) != 0) {
                return Result.IGNORED;
            }
            this.initialCompletedLanes |= lane;
            return this.initialCompletedLanes == this.requiredLanes ? Result.INITIAL_COMPLETE : Result.WAITING;
        }

        // Records the edge pass of a lane and returns the outcome.
        synchronized Result completeEdges(long taskGeneration, int lane) {
            if (taskGeneration != this.generation || (lane & this.requiredLanes) == 0
                    || this.initialCompletedLanes != this.requiredLanes || (this.edgeCompletedLanes & lane) != 0) {
                return Result.IGNORED;
            }
            this.edgeCompletedLanes |= lane;
            return this.edgeCompletedLanes == this.requiredLanes ? Result.COMPLETE : Result.WAITING;
        }

        // Finishes the future once with the published state.
        synchronized void finish(boolean published) {
            if (this.future.isDone()) {
                return;
            }
            this.published = published;
            this.future.set(null);
        }
    }
}
