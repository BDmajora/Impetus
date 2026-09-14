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

// One pass's worth of state (Phosphor's cursor, neighbour scratch and the 34 level queues), split out of the engine so several can run at once on chunks far enough apart; a pass settles everything reachable from the positions it was handed, and the light field it leaves is the same fixpoint whatever order the passes ran in
final class LightPropagator {
    private static final int MAX_LIGHT = LightKey.MAX_LIGHT;

    private final World world;
    private final Profiler profiler;

    // Positions to spread from, bucketed by the light level they were set to
    private final DeduplicatedLongQueue[] brighteningQueues = new DeduplicatedLongQueue[MAX_LIGHT + 1];
    // Positions to clear, bucketed by the light level they held
    private final DeduplicatedLongQueue[] darkeningQueues = new DeduplicatedLongQueue[MAX_LIGHT + 1];

    // Scheduled positions found brighter than stored; new level carried in bits 60-63
    private final DeduplicatedLongQueue initialBrightenings;
    // Scheduled positions found darker than stored
    private final DeduplicatedLongQueue initialDarkenings;

    // The pass is single-threaded per propagator, so the cursor lives in fields rather than being threaded through every call
    private final MutableBlockPos currentPos = new MutableBlockPos();
    private final NeighborInfo[] neighborInfos = new NeighborInfo[6];

    private DeduplicatedLongQueue currentQueue;
    private Chunk currentChunk;
    private long currentChunkIdentifier;
    private long currentData;

    private boolean isNeighborDataValid;

    // Positions dequeued during the current pass, published to Fulgor when it ends
    private long processedThisPass;

    // Render notifications are collected here when the pass runs off the owning thread, and replayed by it afterwards
    private LongArrayList deferredNotifications;

    // Scratch queue a partitioned batch is loaded into before a pass, since the pass drains a queue
    private final DeduplicatedLongQueue scratch;

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

    // Runs one full pass over a queue of scheduled positions; deferNotifications is set when the caller is not the thread allowed to poke the renderer
    void run(EnumSkyBlock lightType, DeduplicatedLongQueue queue, boolean deferNotifications) {
        this.currentChunkIdentifier = -1;
        this.currentChunk = null;
        this.processedThisPass = 0L;
        this.deferredNotifications = deferNotifications ? new LongArrayList() : null;

        try {
            propagate(lightType, queue);
        } finally {
            // The pass reaches foreign code via notifyLightSet and block light values and can throw; the counter is still published so the statistics stay honest
            Fulgor.recordProcessed(this.processedThisPass);
        }
    }

    // Loads a partitioned batch into the scratch queue; the keys are already unique, so no dedup set is kept for it
    DeduplicatedLongQueue fill(LongArrayList keys) {
        for (int i = 0; i < keys.size(); i++) {
            this.scratch.enqueue(keys.getLong(i));
        }
        return this.scratch;
    }

    // The scratch queue, for a caller that filled it in several steps
    DeduplicatedLongQueue scratchQueue() {
        return this.scratch;
    }

    // Notifications collected during an off-thread pass, or null when there were none to defer
    LongArrayList takeDeferredNotifications() {
        LongArrayList notifications = this.deferredNotifications;
        this.deferredNotifications = null;
        return notifications;
    }

    // The pass itself: sort scheduled positions into brighten/darken, then settle each level in lockstep
    private void propagate(EnumSkyBlock lightType, DeduplicatedLongQueue queue) {
        this.profiler.startSection("fulgor");
        this.profiler.startSection("sort");

        // Sort scheduled positions into "brighter" and "darker" without acting yet; a position can be reached by both, and scheduling from here would enqueue it once per neighbour that noticed
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
            int newLight = (int) (this.currentData >> LightKey.S_L & LightKey.M_L);

            if (newLight > getCursorCachedLight(lightType)) {
                // Setting the light here as well as queueing is what stops a position being scheduled twice: the second visit sees the new value and stops. M_POS strips the light field off the key
                enqueueBrightening(this.currentPos, this.currentData & LightKey.M_POS, newLight, this.currentChunk, lightType);
            }
        }

        beginDraining(this.initialDarkenings);

        while (nextItem()) {
            int oldLight = getCursorCachedLight(lightType);

            if (oldLight != 0) {
                enqueueDarkening(this.currentPos, this.currentData, oldLight, this.currentChunk, lightType);
            }
        }

        this.profiler.endStartSection("propagate");

        // Brightest to darkest, darkening then brightening at each level; a position can only be enqueued below the level being processed, so nothing is revisited
        for (int currentLight = MAX_LIGHT; currentLight >= 0; currentLight--) {
            beginDraining(this.darkeningQueues[currentLight]);

            while (nextItem()) {
                // Something else brightened this position after it was queued; nothing to clear.
                if (getCursorCachedLight(lightType) >= currentLight) {
                    continue;
                }

                IBlockState state = LightUtil.posToState(this.currentPos, this.currentChunk);
                int luminosity = getCursorLuminosity(state, lightType);
                int opacity = luminosity >= MAX_LIGHT - 1
                        ? 1 // Irrelevant: nothing this bright can be darkened by its own opacity.
                        : getPosOpacity(this.currentPos, state, this.currentChunk);

                if (calculateNewLightFromCursor(luminosity, opacity, lightType) < currentLight) {
                    // We got darker, so anything we were lighting must be reconsidered, ignoring neighbours about to be darkened themselves or they would prop each other up
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
                            // We could have been its light source, so it has to be re-derived too.
                            enqueueDarkening(neighborPos, info.key, info.light, neighborChunk, lightType);
                        } else {
                            // Brighter than we can account for, so it has an independent source; processing order guarantees nobody darkens it later, so it is safe to be lit by
                            newLight = Math.max(newLight, info.light - opacity);
                        }
                    }

                    enqueueBrighteningFromCursor(newLight, lightType);
                } else {
                    // A false alarm, still as bright as before; the value was zeroed when queued so it must be put back, queued rather than spread so neighbours are not scheduled twice
                    enqueueBrighteningFromCursor(currentLight, lightType);
                }
            }

            beginDraining(this.brighteningQueues[currentLight]);

            while (nextItem()) {
                // Anything but an exact match means the position moved on after being queued, and whatever moved it queued its own follow-up
                if (getCursorCachedLight(lightType) != currentLight) {
                    continue;
                }

                notifyLightSet(this.currentPos, this.currentData);

                if (currentLight > 1) {
                    spreadLightFromCursor(currentLight, lightType);
                }
            }
        }

        this.profiler.endSection();
        this.profiler.endSection();
    }

    // Straight through on the owning thread; collected for later on any other, since the renderer's light hooks are not written for a worker
    private void notifyLightSet(BlockPos pos, long key) {
        if (this.deferredNotifications != null) {
            this.deferredNotifications.add(key & LightKey.M_POS);
            return;
        }
        this.world.notifyLightSet(pos);
    }

    // Points the cursor at a queue and clears its dedup set; safe because every queue is fully filled before it is drained
    private void beginDraining(DeduplicatedLongQueue queue) {
        this.currentQueue = queue;
        queue.resetDeduplication();
    }

    // Advances the cursor; returns whether there was anything left
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

    // Fills neighborInfos for the cursor position if it moved since the last fill; an unloaded neighbour gets a null chunk (skipped by every caller) with its other fields left stale on purpose
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

    // The brightest a position could legitimately be, given its own luminosity and its neighbours'
    private int calculateNewLightFromCursor(EnumSkyBlock lightType) {
        IBlockState state = LightUtil.posToState(this.currentPos, this.currentChunk);

        int luminosity = getCursorLuminosity(state, lightType);
        int opacity = luminosity >= MAX_LIGHT - 1
                ? 1
                : getPosOpacity(this.currentPos, state, this.currentChunk);

        return calculateNewLightFromCursor(luminosity, opacity, lightType);
    }

    // Light a block should have given its own emission and the brightest neighbour minus opacity
    private int calculateNewLightFromCursor(int luminosity, int opacity, EnumSkyBlock lightType) {
        // Already at least as bright as anything could make it, so the neighbours cannot matter.
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

    // Queues every neighbour that would get brighter from the cursor's new value
    private void spreadLightFromCursor(int currentLight, EnumSkyBlock lightType) {
        fetchNeighborDataFromCursor(lightType);

        for (NeighborInfo info : this.neighborInfos) {
            Chunk neighborChunk = info.chunk;

            // Opacity is at least 1, so a neighbour at or above our level can never be brightened.
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

    // Cursor-relative overload so the hot loop avoids re-fetching chunk and data
    private void enqueueBrighteningFromCursor(int newLight, EnumSkyBlock lightType) {
        enqueueBrightening(this.currentPos, this.currentData, newLight, this.currentChunk, lightType);
    }

    // Queues the position for spreading and writes the new level, so a second visit is a no-op
    private void enqueueBrightening(BlockPos pos, long key, int newLight, Chunk chunk, EnumSkyBlock lightType) {
        this.brighteningQueues[newLight].enqueue(key);

        chunk.setLightFor(lightType, pos, newLight);
    }

    // Queues the position for clearing and zeroes it, so a second visit is a no-op
    private void enqueueDarkening(BlockPos pos, long key, int oldLight, Chunk chunk, EnumSkyBlock lightType) {
        this.darkeningQueues[oldLight].enqueue(key);

        chunk.setLightFor(lightType, pos, 0);
    }

    // Reads the chunk's cached level for the cursor without a world lookup
    private int getCursorCachedLight(EnumSkyBlock lightType) {
        return ((ChunkLightingData) this.currentChunk).fulgor$getCachedLightFor(lightType, this.currentPos);
    }

    // Same read as ChunkLightingData.fulgor$getCachedLightFor for an already-resolved section; fetchNeighborDataFromCursor needs the section anyway
    private int getCachedLightFor(Chunk chunk, ExtendedBlockStorage section, BlockPos pos, EnumSkyBlock type) {
        if (section == Chunk.NULL_BLOCK_STORAGE) {
            return type == EnumSkyBlock.SKY && chunk.canSeeSky(pos) ? type.defaultLightValue : 0;
        }

        int x = pos.getX() & 15;
        int y = pos.getY() & 15;
        int z = pos.getZ() & 15;

        if (type == EnumSkyBlock.SKY) {
            // Asked live rather than cached: WorldProvider.hasSkyLight() is only populated by registerWorld, after the World constructor this engine is built in
            return this.world.provider.hasSkyLight() ? section.getSkyLight(x, y, z) : 0;
        }

        return type == EnumSkyBlock.BLOCK ? section.getBlockLight(x, y, z) : type.defaultLightValue;
    }

    // For skylight, luminosity is a heightmap property: open sky above means a full-strength source, otherwise not a source at all
    private int getCursorLuminosity(IBlockState state, EnumSkyBlock lightType) {
        if (lightType == EnumSkyBlock.SKY) {
            return this.currentChunk.canSeeSky(this.currentPos) ? EnumSkyBlock.SKY.defaultLightValue : 0;
        }

        return MathHelper.clamp(LightUtil.getLightValue(state, this.world, this.currentPos, this.currentChunk),
                0, MAX_LIGHT);
    }

    // Clamped to at least 1: a zero-opacity step would let light travel unattenuated forever
    private int getPosOpacity(BlockPos pos, IBlockState state, Chunk chunk) {
        return MathHelper.clamp(LightUtil.getLightOpacity(state, this.world, pos, chunk), 1, MAX_LIGHT);
    }

    // Loaded-only lookup; null for an unloaded chunk, which callers treat as a hard boundary
    private Chunk getChunk(BlockPos pos) {
        return this.world.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    // Scratch space for one neighbour, reused across the whole pass
    private static final class NeighborInfo {
        final MutableBlockPos pos = new MutableBlockPos();

        Chunk chunk;
        ExtendedBlockStorage section;

        int light;
        long key;
    }
}
