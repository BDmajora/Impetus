package com.bdmajora.fulgor.lighting;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.api.ChunkLightingData;
import com.bdmajora.fulgor.collections.DeduplicatedLongQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

// Breadth first light propagator that drains darkening and brightening queues by light level.
final class LightPropagator {
    // Highest light level.
    private static final int MAX_LIGHT = LightKey.MAX_LIGHT;

    // World the light is written to.
    private final World world;
    // Profiler of the world.
    private final Profiler profiler;

    // Brightening queues by light level.
    private final DeduplicatedLongQueue[] brighteningQueues = new DeduplicatedLongQueue[MAX_LIGHT + 1];
    // Darkening queues by light level.
    private final DeduplicatedLongQueue[] darkeningQueues = new DeduplicatedLongQueue[MAX_LIGHT + 1];

    // Positions found brighter than their stored light.
    private final DeduplicatedLongQueue initialBrightenings;
    // Positions found darker than their stored light.
    private final DeduplicatedLongQueue initialDarkenings;

    // Position of the current item.
    private final MutableBlockPos currentPos = new MutableBlockPos();
    // Data of the six neighbors of the current item.
    private final NeighborInfo[] neighborInfos = new NeighborInfo[6];

    // Queue being drained or null.
    private DeduplicatedLongQueue currentQueue;
    // Chunk of the current item or null when unloaded.
    private Chunk currentChunk;
    // Chunk bits of the current key.
    private long currentChunkIdentifier;
    // Key of the current item.
    private long currentData;

    // Valid state of the neighbor data for the current item.
    private boolean isNeighborDataValid;

    // Number of items handled in the current run.
    private long processedThisPass;

    // Positions whose light change is reported later or null.
    private LongArrayList deferredNotifications;

    // Queue that callers fill before a run.
    private final DeduplicatedLongQueue scratch;

    // Creates a propagator whose queues share one segment pool.
    LightPropagator(World world, boolean deduplicate, int queueCapacity) {
        this.world = world;
        this.profiler = world.profiler;

        DeduplicatedLongQueue.Pool pool = new DeduplicatedLongQueue.Pool();

        this.initialBrightenings = new DeduplicatedLongQueue(pool, queueCapacity, deduplicate);
        this.initialDarkenings = new DeduplicatedLongQueue(pool, queueCapacity, deduplicate);
        this.scratch = new DeduplicatedLongQueue(pool, queueCapacity, false);

        for (int i = 0; i <= MAX_LIGHT; i++) {
            this.brighteningQueues[i] = new DeduplicatedLongQueue(pool, queueCapacity, deduplicate);
            this.darkeningQueues[i] = new DeduplicatedLongQueue(pool, queueCapacity, deduplicate);
        }

        for (int i = 0; i < this.neighborInfos.length; i++) {
            this.neighborInfos[i] = new NeighborInfo();
        }
    }

    // Updates the light of every queued position and records the handled count.
    void run(EnumSkyBlock lightType, DeduplicatedLongQueue queue, boolean deferNotifications) {
        this.currentChunkIdentifier = -1;
        this.currentChunk = null;
        this.processedThisPass = 0L;
        this.deferredNotifications = deferNotifications ? new LongArrayList() : null;

        try {
            propagate(lightType, queue);
        } finally {
            Fulgor.recordProcessed(this.processedThisPass);
        }
    }

    // Adds keys to the scratch queue and returns it.
    DeduplicatedLongQueue fill(LongArrayList keys) {
        for (int i = 0; i < keys.size(); i++) {
            this.scratch.enqueue(keys.getLong(i));
        }
        return this.scratch;
    }

    // Returns the scratch queue.
    DeduplicatedLongQueue scratchQueue() {
        return this.scratch;
    }

    // Returns and forgets the deferred notifications.
    LongArrayList takeDeferredNotifications() {
        LongArrayList notifications = this.deferredNotifications;
        this.deferredNotifications = null;
        return notifications;
    }

    // Sorts the queued positions into brighter and darker ones and spreads both from the highest level down.
    private void propagate(EnumSkyBlock lightType, DeduplicatedLongQueue queue) {
        this.profiler.startSection("fulgor");
        this.profiler.startSection("sort");
        // Ends both profiler sections on every path
        try {
            this.drain(lightType, queue);
        } finally {
            this.profiler.endSection();
            this.profiler.endSection();
        }
    }

    // Runs the sort and seed and propagate stages.
    private void drain(EnumSkyBlock lightType, DeduplicatedLongQueue queue) {

        beginDraining(queue);

        while (nextItem()) {
            if (this.currentChunk == null) {
                continue;
            }

            int oldLight = getCursorCachedLight(lightType);
            int newLight = calculateNewLightFromCursor(lightType);

            if (oldLight < newLight) {
                this.initialBrightenings.enqueue(((long) newLight << LightKey.S_L) | this.currentData);
            } else if (oldLight > newLight) {
                this.initialDarkenings.enqueue(this.currentData);
            }
        }

        this.profiler.endStartSection("seed");

        beginDraining(this.initialBrightenings);

        while (nextItem()) {
            if (this.currentChunk == null) {
                continue;
            }

            int newLight = (int) (this.currentData >> LightKey.S_L & LightKey.M_L);

            if (newLight > getCursorCachedLight(lightType)) {
                enqueueBrightening(this.currentPos, this.currentData & LightKey.M_POS, newLight, this.currentChunk, lightType);
            }
        }

        beginDraining(this.initialDarkenings);

        while (nextItem()) {
            if (this.currentChunk == null) {
                continue;
            }

            int oldLight = getCursorCachedLight(lightType);

            if (oldLight != 0) {
                enqueueDarkening(this.currentPos, this.currentData, oldLight, this.currentChunk, lightType);
            }
        }

        this.profiler.endStartSection("propagate");

        for (int currentLight = MAX_LIGHT; currentLight >= 0; currentLight--) {
            beginDraining(this.darkeningQueues[currentLight]);

            while (nextItem()) {
                if (this.currentChunk == null) {
                    continue;
                }

                if (getCursorCachedLight(lightType) >= currentLight) {
                    continue;
                }

                IBlockState state = LightUtil.posToState(this.currentPos, this.currentChunk);
                int luminosity = getCursorLuminosity(state, lightType);
                int opacity = luminosity >= MAX_LIGHT - 1
                        ? 1
                        : getPosOpacity(this.currentPos, state, this.currentChunk);

                if (calculateNewLightFromCursor(luminosity, opacity, lightType) < currentLight) {
                    int newLight = luminosity;

                    fetchNeighborDataFromCursor(lightType);

                    for (NeighborInfo info : this.neighborInfos) {
                        Chunk neighborChunk = info.chunk;

                        if (neighborChunk == null || info.light == 0) {
                            continue;
                        }

                        MutableBlockPos neighborPos = info.pos;
                        IBlockState neighborState = LightUtil.posToState(neighborPos, info.section);

                        if (currentLight - getPosOpacity(neighborPos, neighborState, neighborChunk) >= info.light) {
                            enqueueDarkening(neighborPos, info.key, info.light, neighborChunk, lightType);
                        } else {
                            newLight = Math.max(newLight, info.light - opacity);
                        }
                    }

                    enqueueBrighteningFromCursor(newLight, lightType);
                } else {
                    enqueueBrighteningFromCursor(currentLight, lightType);
                }
            }

            beginDraining(this.brighteningQueues[currentLight]);

            while (nextItem()) {
                if (this.currentChunk == null) {
                    continue;
                }

                if (getCursorCachedLight(lightType) != currentLight) {
                    continue;
                }

                notifyLightSet(this.currentPos, this.currentData);

                if (currentLight > 1) {
                    spreadLightFromCursor(currentLight, lightType);
                }
            }
        }
    }

    // Reports a light change now or records it for later.
    private void notifyLightSet(BlockPos pos, long key) {
        if (this.deferredNotifications != null) {
            this.deferredNotifications.add(key & LightKey.M_POS);
            return;
        }
        this.world.notifyLightSet(pos);
    }

    // Starts draining a queue and forgets its seen values.
    private void beginDraining(DeduplicatedLongQueue queue) {
        this.currentQueue = queue;
        queue.resetDeduplication();
    }

    // Moves to the next item of the queue and returns false when it is empty.
    private boolean nextItem() {
        if (this.currentQueue.isEmpty()) {
            this.currentQueue = null;
            return false;
        }

        this.currentData = this.currentQueue.dequeue();
        this.isNeighborDataValid = false;

        LightKey.decode(this.currentPos, this.currentData);

        long chunkIdentifier = this.currentData & LightKey.M_CHUNK;

        if (this.currentChunkIdentifier != chunkIdentifier) {
            this.currentChunk = getChunk(this.currentPos);
            this.currentChunkIdentifier = chunkIdentifier;
        }

        this.processedThisPass++;

        return true;
    }

    // Reads the chunk and section and light of every neighbor once per item.
    private void fetchNeighborDataFromCursor(EnumSkyBlock lightType) {
        if (this.isNeighborDataValid) {
            return;
        }

        this.isNeighborDataValid = true;

        for (int i = 0; i < this.neighborInfos.length; i++) {
            NeighborInfo info = this.neighborInfos[i];

            long neighborKey = info.key = this.currentData + LightKey.NEIGHBOR_SHIFTS[i];

            if ((neighborKey & LightKey.Y_CHECK) != 0) {
                info.chunk = null;
                info.section = null;
                continue;
            }

            MutableBlockPos neighborPos = LightKey.decode(info.pos, neighborKey);

            Chunk neighborChunk = (neighborKey & LightKey.M_CHUNK) == this.currentChunkIdentifier
                    ? this.currentChunk
                    : getChunk(neighborPos);

            info.chunk = neighborChunk;

            if (neighborChunk == null) {
                info.section = null;
                continue;
            }

            ExtendedBlockStorage section = neighborChunk.getBlockStorageArray()[neighborPos.getY() >> 4];

            info.section = section;
            info.light = getCachedLightFor(neighborChunk, section, neighborPos, lightType);
        }
    }

    // Returns the light the current item gets from its own emission and its neighbors.
    private int calculateNewLightFromCursor(EnumSkyBlock lightType) {
        IBlockState state = LightUtil.posToState(this.currentPos, this.currentChunk);

        int luminosity = getCursorLuminosity(state, lightType);
        int opacity = luminosity >= MAX_LIGHT - 1
                ? 1
                : getPosOpacity(this.currentPos, state, this.currentChunk);

        return calculateNewLightFromCursor(luminosity, opacity, lightType);
    }

    // Returns the larger of the emission and the brightest neighbor less the opacity.
    private int calculateNewLightFromCursor(int luminosity, int opacity, EnumSkyBlock lightType) {
        if (luminosity >= MAX_LIGHT - opacity) {
            return luminosity;
        }

        int newLight = luminosity;

        fetchNeighborDataFromCursor(lightType);

        for (NeighborInfo info : this.neighborInfos) {
            if (info.chunk == null) {
                continue;
            }

            newLight = Math.max(info.light - opacity, newLight);
        }

        return newLight;
    }

    // Queues every neighbor that becomes brighter through the current item.
    private void spreadLightFromCursor(int currentLight, EnumSkyBlock lightType) {
        fetchNeighborDataFromCursor(lightType);

        for (NeighborInfo info : this.neighborInfos) {
            Chunk neighborChunk = info.chunk;

            if (neighborChunk == null || currentLight <= info.light) {
                continue;
            }

            MutableBlockPos neighborPos = info.pos;
            IBlockState neighborState = LightUtil.posToState(neighborPos, info.section);

            int newLight = currentLight - getPosOpacity(neighborPos, neighborState, neighborChunk);

            if (newLight > info.light) {
                enqueueBrightening(neighborPos, info.key, newLight, neighborChunk, lightType);
            }
        }
    }

    // Queues the current item as brightened.
    private void enqueueBrighteningFromCursor(int newLight, EnumSkyBlock lightType) {
        enqueueBrightening(this.currentPos, this.currentData, newLight, this.currentChunk, lightType);
    }

    // Queues a brightened position and stores its light.
    private void enqueueBrightening(BlockPos pos, long key, int newLight, Chunk chunk, EnumSkyBlock lightType) {
        this.brighteningQueues[newLight].enqueue(key);

        chunk.setLightFor(lightType, pos, newLight);
    }

    // Queues a darkened position and clears its light.
    private void enqueueDarkening(BlockPos pos, long key, int oldLight, Chunk chunk, EnumSkyBlock lightType) {
        this.darkeningQueues[oldLight].enqueue(key);

        chunk.setLightFor(lightType, pos, 0);
    }

    // Returns the stored light of the current item.
    private int getCursorCachedLight(EnumSkyBlock lightType) {
        return ((ChunkLightingData) this.currentChunk).fulgor$getCachedLightFor(lightType, this.currentPos);
    }

    // Returns the stored light of a position in a section.
    private int getCachedLightFor(Chunk chunk, ExtendedBlockStorage section, BlockPos pos, EnumSkyBlock type) {
        if (section == Chunk.NULL_BLOCK_STORAGE) {
            return type == EnumSkyBlock.SKY && chunk.canSeeSky(pos) ? type.defaultLightValue : 0;
        }

        int x = pos.getX() & 15;
        int y = pos.getY() & 15;
        int z = pos.getZ() & 15;

        if (type == EnumSkyBlock.SKY) {
            return this.world.provider.hasSkyLight() ? section.getSkyLight(x, y, z) : 0;
        }

        return type == EnumSkyBlock.BLOCK ? section.getBlockLight(x, y, z) : type.defaultLightValue;
    }

    // Returns the light the current item emits.
    private int getCursorLuminosity(IBlockState state, EnumSkyBlock lightType) {
        if (lightType == EnumSkyBlock.SKY) {
            return this.currentChunk.canSeeSky(this.currentPos) ? EnumSkyBlock.SKY.defaultLightValue : 0;
        }

        return MathHelper.clamp(LightUtil.getLightValue(state, this.world, this.currentPos, this.currentChunk),
                0, MAX_LIGHT);
    }

    // Returns the opacity of a position between 1 and the highest light.
    private int getPosOpacity(BlockPos pos, IBlockState state, Chunk chunk) {
        return MathHelper.clamp(LightUtil.getLightOpacity(state, this.world, pos, chunk), 1, MAX_LIGHT);
    }

    // Returns the loaded chunk of a position or null.
    private Chunk getChunk(BlockPos pos) {
        return this.world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    // Data of one neighbor of the current item.
    private static final class NeighborInfo {
        // Position of the neighbor.
        final MutableBlockPos pos = new MutableBlockPos();

        // Chunk of the neighbor or null when unloaded or out of height.
        Chunk chunk;
        // Section of the neighbor or null.
        ExtendedBlockStorage section;

        // Stored light of the neighbor.
        int light;
        // Key of the neighbor.
        long key;
    }
}
