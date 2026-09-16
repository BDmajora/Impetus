package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.FulgorRenderBridge;
import com.bdmajora.fulgor.async.ChunkLightHelper;
import com.bdmajora.fulgor.async.SWMRNibbleArray;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Starlight's BFS engine (via Pulsar's port of SuperNova): a queue entry is a long with x in bits 0-5, z in 6-11, y in 12-27 (all relative to the window), level in 28-31, a direction bitset in 32-37 and three flags in 61-63
public abstract class BfsLightEngine extends LightEngineCache {
    protected static final AxisDirection[] AXIS_DIRECTIONS = AxisDirection.values();
    protected static final AxisDirection[] ONLY_HORIZONTAL_DIRECTIONS = {
            AxisDirection.POSITIVE_X, AxisDirection.NEGATIVE_X, AxisDirection.POSITIVE_Z, AxisDirection.NEGATIVE_Z
    };
    protected static final int COORD_X_BITS = 6;
    protected static final int COORD_Z_BITS = 6;
    protected static final int COORD_Y_BITS = 16;
    protected static final int COORD_Y_MASK = (1 << COORD_Y_BITS) - 1;
    protected static final int LIGHT_LEVEL_SHIFT = COORD_X_BITS + COORD_Z_BITS + COORD_Y_BITS;
    protected static final int DIRECTION_SHIFT = LIGHT_LEVEL_SHIFT + 4;
    protected static final long COORD_MASK = (1L << LIGHT_LEVEL_SHIFT) - 1;
    protected static final long FLAG_WRITE_LEVEL = Long.MIN_VALUE >>> 2;
    protected static final long FLAG_RECHECK_LEVEL = Long.MIN_VALUE >>> 1;
    protected static final long FLAG_HAS_SIDED_TRANSPARENT_BLOCKS = Long.MIN_VALUE;
    // Queues start at one section's worth and double on demand up to 8 MB each; past that a task reports overflow and the chunk is relit
    protected static final int INITIAL_QUEUE_SIZE = 1 << 12;
    protected static final int MAX_QUEUE_SIZE = 1 << 20;
    protected static final AxisDirection[][] OLD_CHECK_DIRECTIONS = new AxisDirection[1 << 6][];
    protected static final int ALL_DIRECTIONS_BITSET = (1 << 6) - 1;

    static {
        for (int i = 0; i < OLD_CHECK_DIRECTIONS.length; ++i) {
            List<AxisDirection> directions = new ArrayList<>();
            for (int bitset = i, len = Integer.bitCount(i), index = 0; index < len; ++index, bitset ^= (-bitset & bitset)) {
                directions.add(AXIS_DIRECTIONS[Integer.numberOfTrailingZeros(bitset)]);
            }
            OLD_CHECK_DIRECTIONS[i] = directions.toArray(new AxisDirection[0]);
        }
    }

    protected final boolean skylightPropagator;
    private final LightSectionProcessor sectionProcessor;
    // Diagnostics accumulated across one task
    public int lastBfsIncreaseTotal;
    public int lastBfsDecreaseTotal;
    public int lastPositionsProcessed;
    protected long[] increaseQueue = new long[INITIAL_QUEUE_SIZE];
    protected int increaseQueueInitialLength;
    protected long[] decreaseQueue = new long[INITIAL_QUEUE_SIZE];
    protected int decreaseQueueInitialLength;
    protected boolean queueOverflowWarned;
    protected boolean queueOverflowed;

    protected BfsLightEngine(boolean skylightPropagator, World world) {
        super(world);
        this.skylightPropagator = skylightPropagator;
        this.sectionProcessor = new LightSectionProcessor(this);
    }

    protected static long encodeCoords(int x, int z, int y, int encodeOffset) {
        return (x + ((long) z << COORD_X_BITS) + ((long) y << (COORD_X_BITS + COORD_Z_BITS)) + encodeOffset) & COORD_MASK;
    }

    protected static long sidedFlag(IBlockState state) {
        return sidedFlag(LightInfo.of(state));
    }

    protected static long sidedFlag(int lightInfo) {
        return (lightInfo & LightInfo.REGISTRY) != 0 ? FLAG_HAS_SIDED_TRANSPARENT_BLOCKS : 0L;
    }

    // Per-section emptiness of a chunk's storage, TRUE for absent or blockless
    public static Boolean[] getEmptySectionsForChunk(Chunk chunk) {
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        Boolean[] ret = new Boolean[16];
        for (int sectionY = 0; sectionY < 16; ++sectionY) {
            ret[sectionY] = isEmptyOfBlocks(sections[sectionY]) ? Boolean.TRUE : Boolean.FALSE;
        }
        return ret;
    }

    // The engine's emptiness is about blocks only; Fulgor widens ExtendedBlockStorage.isEmpty to cover light worth sending, which is not the question here
    static boolean isEmptyOfBlocks(ExtendedBlockStorage section) {
        return section == null || FulgorRenderBridge.isEmptyOfBlocks(section);
    }

    @Override
    protected final void resetTaskState() {
        this.queueOverflowed = false;
        this.queueOverflowWarned = false;
        this.increaseQueueInitialLength = 0;
        this.decreaseQueueInitialLength = 0;
    }

    protected long encodeQueueLevel(int level) {
        return (level & 0xFL) << LIGHT_LEVEL_SHIFT;
    }

    protected boolean isBelowPropagationThreshold(int level) {
        return level <= 1;
    }

    protected abstract void setEmptinessMap(Chunk chunk, boolean[] to);

    @Override
    protected abstract boolean[] getEmptinessMap(Chunk chunk);

    @Override
    protected abstract SWMRNibbleArray[] getNibblesOnChunk(Chunk chunk);

    @Override
    protected abstract boolean canUseChunk(Chunk chunk);

    protected abstract void setNibbles(Chunk chunk, SWMRNibbleArray[] to);

    protected abstract void initNibble(int chunkX, int chunkY, int chunkZ, boolean extrude, boolean initRemovedNibbles);

    protected abstract void setNibbleNull(int chunkX, int chunkY, int chunkZ);

    protected abstract void propagateBlockChanges(Chunk atChunk, int blockX, int blockY, int blockZ);

    protected abstract void checkBlock(int worldX, int worldY, int worldZ);

    protected abstract int calculateLightValue(int worldX, int worldY, int worldZ, int expect);

    protected abstract void lightChunk(Chunk chunk, boolean needsEdgeChecks);

    public final void blockChanged(int blockX, int blockY, int blockZ) {
        if (blockY < 0 || blockY > 255) {
            return;
        }
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        setupCaches(chunkX * 16 + 7, 128, chunkZ * 16 + 7, true, true);
        try {
            Chunk chunk = getChunkInCache(chunkX, chunkZ);
            if (chunk == null) {
                return;
            }
            propagateBlockChanges(chunk, blockX, blockY, blockZ);
            updateVisible();
        } finally {
            destroyCaches();
        }
    }

    // One chunk's batch of block and section changes: sections first (they create or remove nibbles), then the positions against the updated nibbles
    public final void blocksChangedInChunk(int chunkX, int chunkZ, IntOpenHashSet changedPositions, Boolean[] changedSections) {
        this.lastBfsIncreaseTotal = 0;
        this.lastBfsDecreaseTotal = 0;
        this.lastPositionsProcessed = 0;
        setupCaches(chunkX * 16 + 7, 128, chunkZ * 16 + 7, true, true);
        try {
            Chunk chunk = getChunkInCache(chunkX, chunkZ);
            if (chunk == null) {
                return;
            }
            Boolean[] effectiveSectionChanges = reconcileSectionChanges(chunk, changedPositions, changedSections);
            if (effectiveSectionChanges != null) {
                boolean[] ret = handleEmptySectionChanges(chunk, effectiveSectionChanges, false);
                if (ret != null) {
                    setEmptinessMap(chunk, ret);
                }
            }
            if (changedPositions != null && !changedPositions.isEmpty()) {
                processBlockPositionChanges(chunk, chunkX, chunkZ, changedPositions);
            }
            updateVisible();
        } finally {
            destroyCaches();
        }
    }

    // Re-derives section emptiness from the queued positions, since a mod replacing Chunk.setBlockState can hide a section transition from the RETURN hook
    private Boolean[] reconcileSectionChanges(Chunk chunk, IntOpenHashSet changedPositions, Boolean[] changedSections) {
        if (changedPositions == null || changedPositions.isEmpty()) {
            return changedSections;
        }

        Boolean[] effectiveChanges = changedSections;
        boolean[] knownEmptiness = getEmptinessMap(chunk.x, chunk.z);
        IntIterator iterator = changedPositions.iterator();
        while (iterator.hasNext()) {
            int sectionY = (iterator.nextInt() >> 8) >> 4;
            if (sectionY < MIN_SECTION || sectionY > MAX_SECTION) {
                continue;
            }

            boolean isEmpty = isEmptyOfBlocks(getChunkSection(chunk.x, sectionY, chunk.z));
            Boolean queuedValue = effectiveChanges == null ? null : effectiveChanges[sectionY];
            boolean changedFromKnown = knownEmptiness == null || knownEmptiness[sectionY] != isEmpty;

            if (queuedValue != null || changedFromKnown) {
                if (effectiveChanges == null) {
                    effectiveChanges = new Boolean[16];
                }
                effectiveChanges[sectionY] = isEmpty;
            }
        }
        return effectiveChanges;
    }

    protected void processBlockPositionChanges(Chunk chunk, int chunkX, int chunkZ, IntOpenHashSet changedPositions) {
        IntIterator it = changedPositions.iterator();
        while (it.hasNext()) {
            int packed = it.nextInt();
            int worldY = packed >> 8;
            if (worldY < 0 || worldY > 255) {
                continue;
            }
            int worldX = (chunkX << 4) | (packed & 15);
            int worldZ = (chunkZ << 4) | ((packed >> 4) & 15);
            this.lastPositionsProcessed++;
            propagateBlockChanges(chunk, worldX, worldY, worldZ);
        }
    }

    // A chunk's initial pass: fresh NULL nibbles are lit from scratch and handed to the chunk at the end
    public final void light(Chunk chunk, Boolean[] emptySections, boolean checkEdges) {
        int chunkX = chunk.x;
        int chunkZ = chunk.z;
        setupCaches(chunkX * 16 + 7, 128, chunkZ * 16 + 7, true, true);
        try {
            SWMRNibbleArray[] nibbles = ChunkLightHelper.newNullNibbles();
            setChunkInCache(chunkX, chunkZ, chunk);
            setBlocksForChunkInCache(chunkX, chunkZ, chunk.getBlockStorageArray());
            setNibblesForChunkInCache(chunkX, chunkZ, nibbles);
            setEmptinessMapCache(chunkX, chunkZ, getEmptinessMap(chunk));

            boolean[] ret = handleEmptySectionChanges(chunk, emptySections, true);
            if (ret != null) {
                setEmptinessMap(chunk, ret);
            }
            lightChunk(chunk, checkEdges);
            updateVisible();
            setNibbles(chunk, getNibblesFromCache(chunk));
        } finally {
            destroyCaches();
        }
    }

    private SWMRNibbleArray[] getNibblesFromCache(Chunk chunk) {
        SWMRNibbleArray[] nibbles = new SWMRNibbleArray[ChunkLightHelper.LIGHT_SECTIONS];
        for (int cy = MIN_LIGHT_SECTION; cy <= MAX_LIGHT_SECTION; ++cy) {
            nibbles[cy - MIN_LIGHT_SECTION] = getNibbleFromCache(chunk.x, cy, chunk.z);
        }
        return nibbles;
    }

    // Load-time init for a chunk restored with valid saved light: nibble and emptiness-map setup only, no BFS (Starlight's forceHandleEmptySectionChanges)
    public final void loadInChunk(Chunk chunk, Boolean[] emptySections) {
        int chunkX = chunk.x;
        int chunkZ = chunk.z;
        setupCaches(chunkX * 16 + 7, 128, chunkZ * 16 + 7, true, true);
        try {
            Chunk center = getChunkInCache(chunkX, chunkZ);
            if (center == null) {
                return;
            }
            boolean[] ret = handleEmptySectionChanges(center, emptySections, false);
            if (ret != null) {
                setEmptinessMap(center, ret);
            }
            updateVisible();
        } finally {
            destroyCaches();
        }
    }

    public final void checkChunkEdges(int chunkX, int chunkZ, IntOpenHashSet sections) {
        setupCaches(chunkX * 16 + 7, 128, chunkZ * 16 + 7, true, false);
        try {
            Chunk chunk = getChunkInCache(chunkX, chunkZ);
            if (chunk == null) {
                return;
            }
            prepareBatchedEdgeChecks(chunkX, chunkZ);
            IntIterator it = sections.iterator();
            while (it.hasNext()) {
                checkChunkEdge(chunkX, it.nextInt(), chunkZ);
            }
            performLightDecrease();
            updateVisible();
        } finally {
            destroyCaches();
        }
    }

    protected void prepareBatchedEdgeChecks(int chunkX, int chunkZ) {
    }

    // Applies sparse per-section emptiness changes (null means unchanged) and initialises the nibbles around any section that became non-empty
    protected final boolean[] handleEmptySectionChanges(Chunk chunk, Boolean[] emptinessChanges, boolean unlit) {
        return this.sectionProcessor.handleEmptySectionChanges(chunk, emptinessChanges, unlit);
    }

    protected void checkChunkEdge(int chunkX, int chunkY, int chunkZ) {
        this.sectionProcessor.checkChunkEdge(chunkX, chunkY, chunkZ);
    }

    protected boolean areBothEdgeSectionsFull(int currentIndex, int neighbourIndex) {
        return this.nibbleCache[currentIndex].isFullUpdating() && this.nibbleCache[neighbourIndex].isFullUpdating();
    }

    protected boolean areBothEdgeSectionsZero(int currentIndex, int neighbourIndex) {
        return this.nibbleCache[currentIndex].isZeroUpdating() && this.nibbleCache[neighbourIndex].isZeroUpdating();
    }

    protected void checkChunkEdges(Chunk chunk, int fromSection, int toSection) {
        this.sectionProcessor.checkChunkEdges(chunk, fromSection, toSection);
    }

    protected void propagateNeighbourLevels(Chunk chunk, int fromSection, int toSection) {
        this.sectionProcessor.propagateNeighbourLevels(chunk, fromSection, toSection);
    }

    protected final long[] resizeIncreaseQueue() {
        return this.increaseQueue = Arrays.copyOf(this.increaseQueue, Math.min(this.increaseQueue.length * 2, MAX_QUEUE_SIZE));
    }

    protected final long[] resizeDecreaseQueue() {
        return this.decreaseQueue = Arrays.copyOf(this.decreaseQueue, Math.min(this.decreaseQueue.length * 2, MAX_QUEUE_SIZE));
    }

    // False when the entry was dropped for overflow; a caller that appends speculatively and rolls back must check it, since a drop never incremented the length
    protected final boolean appendToIncreaseQueue(long value) {
        long[] queue = this.increaseQueue;
        int idx = this.increaseQueueInitialLength;
        if (idx >= queue.length) {
            if (queue.length >= MAX_QUEUE_SIZE) {
                warnQueueOverflow();
                return false;
            }
            queue = resizeIncreaseQueue();
        }
        queue[idx] = value;
        this.increaseQueueInitialLength = idx + 1;
        return true;
    }

    protected final boolean appendToDecreaseQueue(long value) {
        long[] queue = this.decreaseQueue;
        int idx = this.decreaseQueueInitialLength;
        if (idx >= queue.length) {
            if (queue.length >= MAX_QUEUE_SIZE) {
                warnQueueOverflow();
                return false;
            }
            queue = resizeDecreaseQueue();
        }
        queue[idx] = value;
        this.decreaseQueueInitialLength = idx + 1;
        return true;
    }

    private void warnQueueOverflow() {
        this.queueOverflowed = true;
        if (!this.queueOverflowWarned) {
            this.queueOverflowWarned = true;
            Fulgor.LOGGER.warn("Light queue overflow near chunk ({}, {}); the chunk will be relit", 2 - this.chunkOffsetX, 2 - this.chunkOffsetZ);
        }
    }

    public boolean wasQueueOverflowed() {
        return this.queueOverflowed;
    }

    protected abstract void performLightIncrease();

    protected abstract void performLightDecrease();

    // The six neighbour offsets; ordinal ^ 1 is the opposite, and the bit masks feed the direction bitset of a queue entry
    protected enum AxisDirection {
        POSITIVE_X(1, 0, 0),
        NEGATIVE_X(-1, 0, 0),
        POSITIVE_Z(0, 0, 1),
        NEGATIVE_Z(0, 0, -1),
        POSITIVE_Y(0, 1, 0),
        NEGATIVE_Y(0, -1, 0);

        protected final int x;
        protected final int y;
        protected final int z;
        protected final int oppositeOrdinal;
        protected final long everythingButTheOppositeDirection;
        protected final long everythingButThisDirection;

        AxisDirection(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.oppositeOrdinal = this.ordinal() ^ 1;
            int allBits = (1 << 6) - 1;
            this.everythingButTheOppositeDirection = allBits ^ (1L << (this.ordinal() ^ 1));
            this.everythingButThisDirection = allBits ^ (1L << this.ordinal());
        }
    }
}
