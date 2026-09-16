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

// One per World under the async engine: owns the two lane queues, their workers and the initial-light coordinator (Pulsar's WorldLightManager, itself from SuperNova)
public final class WorldLightManager {
    // A dense skylight edit of this many positions in one chunk is rebuilt in the sky lane rather than propagated position by position, since neighbouring stale columns would re-seed what the previous task cleared (MC-117067 / MC-117094)
    private static final int BULK_SKY_THRESHOLD = 16;

    private final World world;
    private final LoadedChunkMap loadedChunkMap = new LoadedChunkMap();

    // On the server each queue is drained by its own worker; on the client both are drained from the tick, so the engines share storage with the vanilla nibbles and mark render updates directly
    private final LightQueue skyQueue;
    private final LightQueue blockQueue;
    private final InitialLightCoordinator initialLighting;
    private final LightEngineWorker skyWorker;
    private final LightEngineWorker blockWorker;

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

    public void registerChunk(Chunk chunk) {
        this.loadedChunkMap.put(ChunkPos.asLong(chunk.x, chunk.z), chunk);
    }

    public void unregisterChunk(int cx, int cz) {
        this.loadedChunkMap.remove(ChunkPos.asLong(cx, cz));
    }

    public Chunk getLoadedChunk(int chunkX, int chunkZ) {
        return this.loadedChunkMap.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    // Edge checks run horizontally only, so once the four neighbours are ready this chunk's seam light is final and safe to send; 1.12.2 has no light packet to correct a chunk afterwards
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

    public void queueBlockChange(int x, int y, int z) {
        AsyncLightStats.BLOCK_CHANGES.increment();
        if (this.skyQueue != null) {
            this.skyQueue.queueBlockChange(x, y, z);
        }
        this.blockQueue.queueBlockChange(x, y, z);
    }

    // A section's emptiness changed, typically a block placed into a previously absent section
    public void queueSectionChange(int cx, int sectionY, int cz, boolean empty) {
        if (this.skyQueue != null) {
            this.skyQueue.queueSectionChange(cx, sectionY, cz, empty);
        }
        this.blockQueue.queueSectionChange(cx, sectionY, cz, empty);
    }

    public void queueChunkLight(int cx, int cz, Chunk chunk, Boolean[] emptySections) {
        this.initialLighting.queue(cx, cz, chunk, emptySections);
    }

    // The cheap load-time init for a chunk restored with valid saved light; the caller has already marked it ready
    public void queueChunkLoadInit(int cx, int cz, Chunk chunk, Boolean[] emptySections) {
        if (this.skyQueue != null) {
            this.skyQueue.queueChunkLoadInit(cx, cz, chunk, emptySections);
        }
        this.blockQueue.queueChunkLoadInit(cx, cz, chunk, emptySections);
    }

    public void removeChunkFromQueues(int cx, int cz) {
        this.initialLighting.removeChunk(cx, cz);
    }

    public boolean hasUpdates() {
        return (this.skyQueue != null && this.skyQueue.hasWork()) || this.blockQueue.hasWork();
    }

    public boolean hasChunkPendingLight(int cx, int cz) {
        return (this.skyQueue != null && this.skyQueue.hasPendingWork(cx, cz)) || this.blockQueue.hasPendingWork(cx, cz);
    }

    // Client tick: drains both lanes on the main thread; the engines publish straight into the vanilla nibbles and mark render updates, so there is no separate drain
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

        // A dense skylight edit is rebuilt in this lane only; routing it through the two-lane coordinator also rebuilt block light, which could erase a source before its queued removal propagated into an empty neighbouring section
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
                // Emptiness is re-read at execution since a coordinated relight may have absorbed section changes while queued
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

    // Re-queues a full relight after an overflow: a coordinated relight keeps its generation and defers completion, an ordinary batch is promoted to a coordinated relight so the final sync and edge checks run
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
            // A false return means a newer generation is queued; it supersedes this attempt either way, so the old one must not report completion
            return true;
        }

        Fulgor.LOGGER.error("{} lane: chunk ({}, {}) overflowed its queue {} times, giving up", laneName, cx, cz, task.relightAttempts + 1);
        return false;
    }

    // Relights one chunk from scratch and, on the server, resends it once done since there is no light packet to correct clients otherwise
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
                // Packet construction must happen on the server thread
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

    // Whether queued work could still change this chunk's light values, which decides if the SWMR data is safe to persist
    public boolean hasPendingLightWork(int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        if (this.initialLighting.hasPending(key)) {
            return true;
        }
        return (this.skyQueue != null && this.skyQueue.hasPendingLightWork(key)) || this.blockQueue.hasPendingLightWork(key);
    }

    // Waits briefly for queued or in-flight work on a chunk; false on timeout so unload can invalidate the save rather than serialise data a worker may still be writing
    public boolean awaitPendingWork(int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(50L);

        while (true) {
            Future<Void> pending = getPendingWorkFuture(key);
            if (pending == null) {
                return true;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                break;
            }
            try {
                pending.get(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Fulgor.LOGGER.warn("Interrupted while waiting for light work on chunk ({}, {})", cx, cz);
                return false;
            } catch (Exception e) {
                break;
            }
        }

        Fulgor.LOGGER.warn("Timed out waiting for light work on chunk ({}, {})", cx, cz);
        return false;
    }

    private Future<Void> getPendingWorkFuture(long key) {
        Future<Void> future = this.skyQueue == null ? null : this.skyQueue.getPendingWorkFuture(key);
        if (future != null) {
            return future;
        }
        future = this.blockQueue.getPendingWorkFuture(key);
        if (future != null) {
            return future;
        }
        return this.initialLighting.getPendingFuture(key);
    }

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
