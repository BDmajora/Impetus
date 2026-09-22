package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.async.AsyncLitChunk;
import com.bdmajora.fulgor.FulgorRenderBridge;
import com.bdmajora.fulgor.async.ChunkLightHelper;
import com.bdmajora.fulgor.async.SWMRNibbleArray;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
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
    protected final boolean canUseChunk(Chunk chunk) {
        return ((AsyncLitChunk) chunk).fulgor$isLightUsable();
    }

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

    // Every changed position is seeded before one drain, so a dense edit is not re-flooded once per block
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
            checkBlock(worldX, worldY, worldZ);
        }
        performLightDecrease();
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

    // Which vanilla array a published section mirrors into
    protected abstract NibbleArray vanillaLightArray(ExtendedBlockStorage section);

    // Highest level any of the six neighbours can deliver into this cell after per-face absorption, starting from level; returns early once expect is beaten
    protected final int attenuateFromNeighbours(int worldX, int worldY, int worldZ, int expect, int info, int level) {
        int rawOpacity = LightInfo.opacity(info);
        boolean sidedTransparent = rawOpacity > 1 && (info & LightInfo.REGISTRY) != 0;
        int faceBits = LightInfo.faceBits(info);
        int uniformAbsorption = !sidedTransparent ? Math.max(1, rawOpacity) : 0;
        int sectionOffset = this.chunkSectionIndexOffset;

        for (AxisDirection direction : AXIS_DIRECTIONS) {
            int offX = worldX + direction.x;
            int offY = worldY + direction.y;
            int offZ = worldZ + direction.z;

            int sectionIndex = (offX >> 4) + 5 * (offZ >> 4) + (5 * 5) * (offY >> 4) + sectionOffset;
            int localIndex = (offX & 15) | ((offZ & 15) << 4) | ((offY & 15) << 8);

            int neighbourLevel = getLightLevel(sectionIndex, localIndex);

            int absorption = sidedTransparent
                    ? ((faceBits & (1 << direction.ordinal())) != 0 ? rawOpacity : 1)
                    : uniformAbsorption;
            int attenuated = neighbourLevel - absorption;
            if (attenuated > level) {
                level = attenuated;
            }

            if (level > expect) {
                return level;
            }
        }

        return level;
    }

    // Face bits of the entry's own block when it was flagged sided-transparent, so light does not leave through a face the block seals
    private int sourceBlockedFaces(long queueValue, int posX, int posY, int posZ) {
        if ((queueValue & FLAG_HAS_SIDED_TRANSPARENT_BLOCKS) == 0L) {
            return 0;
        }
        int srcIdx = (posX >> 4) + 5 * (posZ >> 4) + (5 * 5) * (posY >> 4) + this.chunkSectionIndexOffset;
        IBlockState srcState = getBlockStateFast(srcIdx, posX & 15, posY & 15, posZ & 15);
        return LightInfo.faceBits(lightInfoAt(srcState, posX, posY, posZ));
    }

    protected final void performLightIncrease() {
        long[] queue = this.increaseQueue;
        int queueReadIndex = 0;
        int queueLength = this.increaseQueueInitialLength;
        this.increaseQueueInitialLength = 0;
        int decodeOffsetX = -this.encodeOffsetX;
        int decodeOffsetY = -this.encodeOffsetY;
        int decodeOffsetZ = -this.encodeOffsetZ;
        int encodeOffset = this.coordinateOffset;
        int sectionOffset = this.chunkSectionIndexOffset;

        while (queueReadIndex < queueLength) {
            long queueValue = queue[queueReadIndex++];

            int posX = ((int) queueValue & 63) + decodeOffsetX;
            int posZ = (((int) queueValue >>> 6) & 63) + decodeOffsetZ;
            int posY = (((int) queueValue >>> 12) & COORD_Y_MASK) + decodeOffsetY;
            int propagatedLevel = (int) ((queueValue >>> LIGHT_LEVEL_SHIFT) & 0xF);
            AxisDirection[] checkDirections = OLD_CHECK_DIRECTIONS[(int) ((queueValue >>> DIRECTION_SHIFT) & 63L)];
            int srcBlockedFaces = sourceBlockedFaces(queueValue, posX, posY, posZ);

            if ((queueValue & FLAG_RECHECK_LEVEL) != 0L) {
                if (getLightLevel(posX, posY, posZ) != propagatedLevel) {
                    continue;
                }
            } else if ((queueValue & FLAG_WRITE_LEVEL) != 0L) {
                setLightLevel(posX, posY, posZ, propagatedLevel);
            }

            for (AxisDirection propagate : checkDirections) {
                if ((srcBlockedFaces & (1 << propagate.ordinal())) != 0) {
                    continue;
                }

                int offX = posX + propagate.x;
                int offY = posY + propagate.y;
                int offZ = posZ + propagate.z;

                int sectionIndex = (offX >> 4) + 5 * (offZ >> 4) + (5 * 5) * (offY >> 4) + sectionOffset;
                int localIndex = (offX & 15) | ((offZ & 15) << 4) | ((offY & 15) << 8);

                if (this.nibbleCache[sectionIndex] == null) {
                    continue;
                }

                int currentLevel = getLightLevel(sectionIndex, localIndex);
                // Absorption is at least one, so a neighbour already at level - 1 cannot be brightened; skips the palette and info reads for about half the frontier
                if (currentLevel >= propagatedLevel - 1) {
                    continue;
                }

                IBlockState destState = getBlockStateFast(sectionIndex, offX & 15, offY & 15, offZ & 15);
                int destInfo = lightInfoAt(destState, offX, offY, offZ);
                int absorption = LightInfo.absorption(destInfo, destState, propagate.oppositeOrdinal);

                int targetLevel = propagatedLevel - absorption;
                if (targetLevel <= currentLevel) {
                    continue;
                }

                // The guards above proved a real change, so the nibble is written directly rather than through setLightLevel's no-op check
                this.nibbleCache[sectionIndex].set(localIndex, targetLevel);
                postLightUpdate(sectionIndex, offX & 15, offY & 15, offZ & 15);

                if (targetLevel > 1) {
                    if (queueLength >= queue.length) {
                        if (queue.length >= MAX_QUEUE_SIZE) {
                            this.queueOverflowed = true;
                            continue;
                        }
                        queue = resizeIncreaseQueue();
                    }
                    queue[queueLength++] = encodeCoords(offX, offZ, offY, encodeOffset)
                            | encodeQueueLevel(targetLevel)
                            | (propagate.everythingButTheOppositeDirection << DIRECTION_SHIFT)
                            | sidedFlag(destInfo);
                }
            }
        }
        this.lastBfsIncreaseTotal += queueLength;
    }

    protected final void performLightDecrease() {
        long[] queue = this.decreaseQueue;
        long[] increaseQueue = this.increaseQueue;
        int queueReadIndex = 0;
        int queueLength = this.decreaseQueueInitialLength;
        this.decreaseQueueInitialLength = 0;
        int increaseQueueLength = this.increaseQueueInitialLength;
        int decodeOffsetX = -this.encodeOffsetX;
        int decodeOffsetY = -this.encodeOffsetY;
        int decodeOffsetZ = -this.encodeOffsetZ;
        int encodeOffset = this.coordinateOffset;
        int sectionOffset = this.chunkSectionIndexOffset;
        // Blocks emit block light only; the sky lane never re-seeds from an emitter
        boolean reseedEmitters = !this.skylightPropagator;

        while (queueReadIndex < queueLength) {
            long queueValue = queue[queueReadIndex++];

            int posX = ((int) queueValue & 63) + decodeOffsetX;
            int posZ = (((int) queueValue >>> 6) & 63) + decodeOffsetZ;
            int posY = (((int) queueValue >>> 12) & COORD_Y_MASK) + decodeOffsetY;
            int propagatedLevel = (int) ((queueValue >>> LIGHT_LEVEL_SHIFT) & 0xF);
            AxisDirection[] checkDirections = OLD_CHECK_DIRECTIONS[(int) ((queueValue >>> DIRECTION_SHIFT) & 63)];
            int srcBlockedFaces = sourceBlockedFaces(queueValue, posX, posY, posZ);

            for (AxisDirection propagate : checkDirections) {
                if ((srcBlockedFaces & (1 << propagate.ordinal())) != 0) {
                    continue;
                }

                int offX = posX + propagate.x;
                int offY = posY + propagate.y;
                int offZ = posZ + propagate.z;

                int sectionIndex = (offX >> 4) + 5 * (offZ >> 4) + (5 * 5) * (offY >> 4) + sectionOffset;

                if (this.nibbleCache[sectionIndex] == null) {
                    continue;
                }

                int localIndex = (offX & 15) | ((offZ & 15) << 4) | ((offY & 15) << 8);
                int currentLevel = getLightLevel(sectionIndex, localIndex);
                if (currentLevel == 0) {
                    continue;
                }
                // No skip for a level-15 neighbour: a sky source must reach the brighter-neighbour branch and be re-queued as a recheck increase, which is what re-floods the region being darkened

                IBlockState state = getBlockStateFast(sectionIndex, offX & 15, offY & 15, offZ & 15);
                int info = lightInfoAt(state, offX, offY, offZ);
                int absorption = LightInfo.absorption(info, state, propagate.oppositeOrdinal);

                int targetLevel = propagatedLevel - absorption;
                long sFlag = sidedFlag(info);

                // Brighter than this decrease can account for: it has its own source, so it is re-checked as an increase rather than cleared
                if (currentLevel > targetLevel) {
                    if (increaseQueueLength >= increaseQueue.length) {
                        if (increaseQueue.length >= MAX_QUEUE_SIZE) {
                            this.queueOverflowed = true;
                            continue;
                        }
                        increaseQueue = resizeIncreaseQueue();
                    }
                    increaseQueue[increaseQueueLength++] = encodeCoords(offX, offZ, offY, encodeOffset)
                            | encodeQueueLevel(currentLevel)
                            | (((long) ALL_DIRECTIONS_BITSET) << DIRECTION_SHIFT)
                            | FLAG_RECHECK_LEVEL
                            | sFlag;
                    continue;
                }

                this.nibbleCache[sectionIndex].set(localIndex, 0);
                postLightUpdate(sectionIndex, offX & 15, offY & 15, offZ & 15);

                if (reseedEmitters) {
                    int emission = LightInfo.emission(info);
                    if (emission > 0) {
                        if (increaseQueueLength >= increaseQueue.length) {
                            if (increaseQueue.length >= MAX_QUEUE_SIZE) {
                                this.queueOverflowed = true;
                                continue;
                            }
                            increaseQueue = resizeIncreaseQueue();
                        }
                        this.nibbleCache[sectionIndex].set(localIndex, emission);
                        postLightUpdate(sectionIndex, offX & 15, offY & 15, offZ & 15);
                        increaseQueue[increaseQueueLength++] = encodeCoords(offX, offZ, offY, encodeOffset)
                                | encodeQueueLevel(emission)
                                | (((long) ALL_DIRECTIONS_BITSET) << DIRECTION_SHIFT)
                                | FLAG_WRITE_LEVEL
                                | sFlag;
                    }
                }

                // Independent of any re-seed: the decrease keeps walking past emitters, or removing a bright source leaves a ghost region behind any dimmer one; the level-1 boundary entries re-flood the cleared region's outer ring from the surrounding light
                if (targetLevel > 0) {
                    if (queueLength >= queue.length) {
                        if (queue.length >= MAX_QUEUE_SIZE) {
                            this.queueOverflowed = true;
                            continue;
                        }
                        queue = resizeDecreaseQueue();
                    }
                    queue[queueLength++] = encodeCoords(offX, offZ, offY, encodeOffset)
                            | encodeQueueLevel(targetLevel)
                            | (propagate.everythingButTheOppositeDirection << DIRECTION_SHIFT)
                            | sFlag;
                }
            }
        }

        this.lastBfsDecreaseTotal += queueLength;
        this.increaseQueueInitialLength = increaseQueueLength;
        performLightIncrease();
    }

    // Mirrors the published section into the lane's vanilla array; a no-op on the client where the SWMR array shares the vanilla storage
    @Override
    protected final void onNibbleVisible(int cacheIndex, SWMRNibbleArray nibble) {
        if (nibble == null) {
            return;
        }
        int sectionY = cacheIndex / 25 - this.chunkOffsetY;
        if (sectionY < MIN_SECTION || sectionY > MAX_SECTION) {
            return;
        }
        ExtendedBlockStorage section = this.sectionCache[cacheIndex];
        if (section == null) {
            return;
        }
        byte[] srcData = nibble.getVisibleData();
        if (srcData == null) {
            return;
        }
        NibbleArray vanilla = vanillaLightArray(section);
        if (vanilla == null) {
            return;
        }
        byte[] dst = vanilla.getData();
        if (dst != srcData) {
            System.arraycopy(srcData, 0, dst, 0, srcData.length);
        }
    }

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
        // Step along a chunk's edge facing this (horizontal) direction: an X-facing edge runs along Z and vice versa
        protected final int edgeStepX;
        protected final int edgeStepZ;

        AxisDirection(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.oppositeOrdinal = this.ordinal() ^ 1;
            int allBits = (1 << 6) - 1;
            this.everythingButTheOppositeDirection = allBits ^ (1L << (this.ordinal() ^ 1));
            this.everythingButThisDirection = allBits ^ (1L << this.ordinal());
            this.edgeStepX = x != 0 ? 0 : 1;
            this.edgeStepZ = x != 0 ? 1 : 0;
        }

        // First block X of the edge column: the chunk's own boundary layer, or (outside) the neighbour's layer just past it
        protected int edgeStartX(int chunkX, boolean outside) {
            int base = chunkX << 4;
            if (this.x == 0) {
                return base;
            }
            return this.x < 0 ? base - (outside ? 1 : 0) : base + (outside ? 16 : 15);
        }

        protected int edgeStartZ(int chunkZ, boolean outside) {
            int base = chunkZ << 4;
            if (this.z == 0) {
                return base;
            }
            return this.z < 0 ? base - (outside ? 1 : 0) : base + (outside ? 16 : 15);
        }
    }
}
