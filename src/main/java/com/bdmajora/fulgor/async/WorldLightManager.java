package com.bdmajora.fulgor.async;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.async.engine.BfsLightEngine;
import com.bdmajora.fulgor.async.engine.BlockLightEngine;
import com.bdmajora.fulgor.async.engine.SkyLightEngine;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

// Owner of the light engines and queue of one world.
public final class WorldLightManager {
    // Changed position count above which sky light is relit in bulk.
    private static final int BULK_SKY_THRESHOLD = 16;

    // World of the manager.
    private final World world;
    // Loaded chunks.
    private final LoadedChunkMap loadedChunkMap = new LoadedChunkMap();

    // Queue of sky light work.
    private final LightQueue skyQueue;
    // Queue of block light work.
    private final LightQueue blockQueue;
    // Coordinator of initial lighting.
    private final InitialLightCoordinator initialLighting;
    // Worker of sky light.
    private final LightEngineWorker skyWorker;
    // Worker of block light.
    private final LightEngineWorker blockWorker;

    // Creates a world light manager from its world and has sky light.
    public WorldLightManager(World world, boolean hasSkyLight) {
        this.world = world;
        this.skyQueue = hasSkyLight ? new LightQueue() : null;
        this.blockQueue = new LightQueue();
        this.initialLighting = new InitialLightCoordinator(this.loadedChunkMap, this.skyQueue, this.blockQueue);
        boolean threaded = !world.isRemote;
        this.skyWorker = hasSkyLight
                ? new LightEngineWorker(this.skyQueue, () -> new SkyLightEngine(world),
                        (task, engine) -> processTask(task, engine, InitialLightCoordinator.LANE_SKY), "Sky", threaded)
                : null;
        this.blockWorker = new LightEngineWorker(this.blockQueue, () -> new BlockLightEngine(world),
                (task, engine) -> processTask(task, engine, InitialLightCoordinator.LANE_BLOCK), "Block", threaded);
    }

    // Registers the chunk.
    public void registerChunk(Chunk chunk) {
        this.loadedChunkMap.put(ChunkPos.asLong(chunk.x, chunk.z), chunk);
    }

    // Unregisters the chunk.
    public void unregisterChunk(int cx, int cz) {
        this.loadedChunkMap.remove(ChunkPos.asLong(cx, cz));
    }

    // Returns the loaded chunk.
    public Chunk getLoadedChunk(int chunkX, int chunkZ) {
        return this.loadedChunkMap.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    // Returns whether the neighbours of a chunk have their light.
    public boolean areNeighboursLightReady(int cx, int cz) {
        for (int i = 0; i < 4; ++i) {
            int nx = cx + ((i == 0) ? 1 : (i == 1) ? -1 : 0);
            int nz = cz + ((i == 2) ? 1 : (i == 3) ? -1 : 0);
            Chunk neighbour = this.loadedChunkMap.get(ChunkPos.asLong(nx, nz));
            if (neighbour == null || !((AsyncLitChunk) neighbour).fulgor$isLightReady()) {
                return false;
            }
        }
        return true;
    }

    // Queues the block change.
    public void queueBlockChange(int x, int y, int z) {
        AsyncLightStats.BLOCK_CHANGES.increment();
        if (this.skyQueue != null) {
            this.skyQueue.queueBlockChange(x, y, z);
        }
        this.blockQueue.queueBlockChange(x, y, z);
    }

    // Queues the section change.
    public void queueSectionChange(int cx, int sectionY, int cz, boolean empty) {
        if (this.skyQueue != null) {
            this.skyQueue.queueSectionChange(cx, sectionY, cz, empty);
        }
        this.blockQueue.queueSectionChange(cx, sectionY, cz, empty);
    }

    // Queues the chunk light.
    public void queueChunkLight(int cx, int cz, Chunk chunk, Boolean[] emptySections) {
        this.initialLighting.queue(cx, cz, chunk, emptySections);
    }

    // Queues the chunk load init.
    public void queueChunkLoadInit(int cx, int cz, Chunk chunk, Boolean[] emptySections) {
        if (this.skyQueue != null) {
            this.skyQueue.queueChunkLoadInit(cx, cz, chunk, emptySections);
        }
        this.blockQueue.queueChunkLoadInit(cx, cz, chunk, emptySections);
    }

    // Removes the chunk from queues.
    public void removeChunkFromQueues(int cx, int cz) {
        this.initialLighting.removeChunk(cx, cz);
    }

    // Returns whether either lane has work.
    public boolean hasUpdates() {
        return (this.skyQueue != null && this.skyQueue.hasWork()) || this.blockQueue.hasWork();
    }

    // Returns whether either lane has work for a chunk.
    public boolean hasChunkPendingLight(int cx, int cz) {
        return (this.skyQueue != null && this.skyQueue.hasPendingWork(cx, cz)) || this.blockQueue.hasPendingWork(cx, cz);
    }

    // Publishes finished light to the client.
    public void processClientUpdates() {
        if (this.skyWorker != null) {
            this.skyWorker.processPending();
        }
        this.blockWorker.processPending();
        if (this.skyQueue != null) {
            this.skyQueue.clearWorkSignal();
        }
        this.blockQueue.clearWorkSignal();
    }

    // Runs one task on a lane.
    private void processTask(ChunkTasks task, BfsLightEngine engine, int lane) {
        boolean sky = lane == InitialLightCoordinator.LANE_SKY;
        LightQueue queue = sky ? this.skyQueue : this.blockQueue;
        String laneName = sky ? "Sky" : "Block";
        long t0 = System.nanoTime();
        int cx = (int) task.chunkCoordinate;
        int cz = (int) (task.chunkCoordinate >> 32);
        boolean finishInitial = task.initialLightChunk != null;
        boolean finishEdges = task.initialLightEdgeGeneration > 0L
                && (sky ? task.queuedEdgeChecksSky : task.queuedEdgeChecksBlock) != null;

        if (this.loadedChunkMap.get(task.chunkCoordinate) == null) {
            if (finishInitial) {
                this.initialLighting.completeInitial(task, lane);
            }
            if (finishEdges) {
                this.initialLighting.completeEdges(task, lane);
            }
            return;
        }

        boolean promoteBulkChange = sky && task.initialLightChunk == null && task.initialLightEdgeGeneration <= 0L
                && task.changedPositions != null && task.changedPositions.size() >= BULK_SKY_THRESHOLD;
        if (promoteBulkChange) {
            Chunk chunk = this.loadedChunkMap.get(task.chunkCoordinate);
            if (chunk != null) {
                try {
                    int attempts = 0;
                    boolean overflowed;
                    do {
                        engine.light(chunk, BfsLightEngine.getEmptySectionsForChunk(chunk), false);
                        overflowed = engine.wasQueueOverflowed();
                        attempts++;
                    } while (overflowed && attempts <= InitialLightCoordinator.MAX_RELIGHT_ATTEMPTS);

                    if (overflowed) {
                        AsyncLightStats.QUEUE_OVERFLOWS.increment();
                        Fulgor.LOGGER.error("Sky lane: bulk relight for chunk ({}, {}) overflowed its queue {} times, giving up", cx, cz, attempts);
                    } else {
                        queue.queueEdgeCheckAllSections(cx, cz, true);
                    }
                } catch (Throwable t) {
                    if (this.loadedChunkMap.get(task.chunkCoordinate) != null) {
                        Fulgor.LOGGER.error("Bulk sky relight for chunk ({}, {}) failed", cx, cz, t);
                    }
                }
            }
            AsyncLightStats.SKY_TASKS.increment();
            AsyncLightStats.SKY_WORKER_NANOS.add(System.nanoTime() - t0);
            return;
        }

        boolean valueOverflowed = false;
        boolean edgeOverflowed = false;
        try {
            if (task.loadInitChunk != null && task.initialLightChunk == null) {
                engine.loadInChunk(task.loadInitChunk, task.loadInitEmptySections);
                valueOverflowed |= engine.wasQueueOverflowed();
            }

            if (task.initialLightChunk != null) {
                AsyncLightStats.INITIAL_LIGHTS.increment();
                engine.light(task.initialLightChunk, BfsLightEngine.getEmptySectionsForChunk(task.initialLightChunk), false);
                valueOverflowed |= engine.wasQueueOverflowed();
            } else if (task.changedSectionSet != null || (task.changedPositions != null && !task.changedPositions.isEmpty())) {
                engine.blocksChangedInChunk(cx, cz, task.changedPositions, task.changedSectionSet);
                AsyncLightStats.POSITIONS_PROCESSED.add(engine.lastPositionsProcessed);
                valueOverflowed |= engine.wasQueueOverflowed();
            }

            if ((sky ? task.queuedEdgeChecksSky : task.queuedEdgeChecksBlock) != null) {
                engine.checkChunkEdges(cx, cz, sky ? task.queuedEdgeChecksSky : task.queuedEdgeChecksBlock);
                edgeOverflowed |= engine.wasQueueOverflowed();
            }

            if (valueOverflowed) {
                if (requeueAfterOverflow(queue, task, cx, cz, laneName)) {
                    finishInitial = false;
                    finishEdges = false;
                }
            } else if (edgeOverflowed) {
                if (finishEdges) {
                    if (this.initialLighting.restartAfterEdgeOverflow(task, cx, cz, laneName)) {
                        finishEdges = false;
                    }
                } else if (requeueAfterOverflow(queue, task, cx, cz, laneName)) {
                    finishInitial = false;
                }
            }
        } catch (Throwable t) {
            if (this.loadedChunkMap.get(task.chunkCoordinate) != null) {
                Fulgor.LOGGER.error("{} task for chunk ({}, {}) failed", laneName, cx, cz, t);
            } else {
                Fulgor.LOGGER.warn("{} task for chunk ({}, {}) aborted: chunk unloaded during processing", laneName, cx, cz);
            }
        }

        if (finishInitial) {
            this.initialLighting.completeInitial(task, lane);
        }
        if (finishEdges) {
            this.initialLighting.completeEdges(task, lane);
        }

        long totalNs = System.nanoTime() - t0;
        if (sky) {
            AsyncLightStats.SKY_TASKS.increment();
            AsyncLightStats.SKY_WORKER_NANOS.add(totalNs);
        } else {
            AsyncLightStats.BLOCK_TASKS.increment();
            AsyncLightStats.BLOCK_WORKER_NANOS.add(totalNs);
        }
        if (totalNs > 100_000_000L) {
            Fulgor.LOGGER.warn("Slow {} light task: chunk ({}, {}) took {} ms", laneName, cx, cz, totalNs / 1_000_000L);
        }
    }

    // Requeues a task after a queue overflow and returns whether it was requeued.
    private boolean requeueAfterOverflow(LightQueue queue, ChunkTasks task, int cx, int cz, String laneName) {
        AsyncLightStats.QUEUE_OVERFLOWS.increment();
        if (task.relightAttempts < InitialLightCoordinator.MAX_RELIGHT_ATTEMPTS) {
            Chunk chunk = this.loadedChunkMap.get(task.chunkCoordinate);
            if (chunk == null) {
                return false;
            }
            Boolean[] emptySections = BfsLightEngine.getEmptySectionsForChunk(chunk);
            if (task.initialLightGeneration <= 0L) {
                int edgeRecoveryAttempts = task.initialLightEdgeGeneration > 0L
                        ? Math.min(InitialLightCoordinator.MAX_RELIGHT_ATTEMPTS, task.edgeCheckAttempts + 1) : 0;
                this.initialLighting.queueRecovery(cx, cz, chunk, emptySections, edgeRecoveryAttempts);
                return true;
            }

            queue.requeueChunkLight(cx, cz, chunk, emptySections, task.initialLightGeneration, task.relightAttempts);
            return true;
        }

        Fulgor.LOGGER.error("{} lane: chunk ({}, {}) overflowed its queue {} times, giving up", laneName, cx, cz, task.relightAttempts + 1);
        return false;
    }

    // Queues a full relight of a chunk.
    public boolean forceRelightChunk(int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        Chunk chunk = this.loadedChunkMap.get(key);
        if (chunk == null) {
            return false;
        }
        InitialLightCoordinator.Completion completion = this.initialLighting.queue(cx, cz, chunk, BfsLightEngine.getEmptySectionsForChunk(chunk));

        if (!this.world.isRemote) {
            completion.future.addListener(() -> {
                if (!completion.published) {
                    return;
                }
                MinecraftServer server = this.world.getMinecraftServer();
                if (server == null) {
                    return;
                }
                server.addScheduledTask(() -> {
                    if (!(this.world instanceof WorldServer)) {
                        return;
                    }
                    PlayerChunkMapEntry entry = ((WorldServer) this.world).getPlayerChunkMap().getEntry(cx, cz);
                    Chunk current = this.loadedChunkMap.get(key);
                    if (entry != null && current == completion.chunk && ((AsyncLitChunk) current).fulgor$isLightReady()) {
                        entry.sendPacket(new SPacketChunkData(current, 0xFFFF));
                    }
                });
            }, Runnable::run);
        }
        return true;
    }

    // Returns whether a chunk has initial or value light work pending.
    public boolean hasPendingLightWork(int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        if (this.initialLighting.hasPending(key)) {
            return true;
        }
        return (this.skyQueue != null && this.skyQueue.hasPendingLightWork(key)) || this.blockQueue.hasPendingLightWork(key);
    }

    // Waits for a task already running on an unloading chunk and returns whether the chunk's light is complete.
    // Queued work is never waited for: it runs behind the whole backlog, and the server unloads up to 100 chunks per tick, so
    // lowering the render distance while chunks were still being lit blocked one tick for up to 100 time limits. A chunk
    // with unfinished light is saved without it and lit again when it next loads.
    public boolean finishForUnload(int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(50L);
        return awaitInFlight(this.skyQueue, key, deadline) && awaitInFlight(this.blockQueue, key, deadline)
                && !this.initialLighting.hasPending(key)
                && (this.skyQueue == null || !this.skyQueue.hasQueuedLightWork(key))
                && !this.blockQueue.hasQueuedLightWork(key);
    }

    // Waits until the deadline for the task a lane is running on a chunk and returns whether none is left.
    private static boolean awaitInFlight(LightQueue queue, long key, long deadline) {
        Future<Void> running = queue == null ? null : queue.getInFlightFuture(key);
        if (running == null) {
            return true;
        }
        try {
            running.get(Math.max(0L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Fulgor.LOGGER.warn("Timed out waiting for light work on chunk ({}, {})", (int) key, (int) (key >> 32));
        }
        return false;
    }

    // Shuts down.
    public void shutdown() {
        if (this.skyWorker != null) {
            this.skyWorker.requestStop();
        }
        this.blockWorker.requestStop();
        if (this.skyWorker != null) {
            this.skyWorker.awaitStop();
        }
        this.blockWorker.awaitStop();
    }
}
