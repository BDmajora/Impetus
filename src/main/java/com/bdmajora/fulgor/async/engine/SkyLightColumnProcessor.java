package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.async.ChunkLightHelper;
import com.bdmajora.fulgor.async.SWMRNibbleArray;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.Arrays;

// Processor of the sky light of one column.
final class SkyLightColumnProcessor {
    // Direction set of a column spread.
    private static final long COLUMN_SPREAD = BfsLightEngine.AxisDirection.POSITIVE_Y.everythingButThisDirection;
    // Engine that owns the processor.
    private final SkyLightEngine engine;
    // Flags of sections that need null checks.
    private final boolean[] nullPropagationChecks = new boolean[ChunkLightHelper.LIGHT_SECTIONS];
    // Highest opaque y of each column.
    private final int[] columnMaxY = new int[16 * 16];

    // Creates a sky light column processor from its engine.
    SkyLightColumnProcessor(SkyLightEngine engine) {
        this.engine = engine;
    }

    // Initializes the nibble.
    void initNibble(int chunkX, int chunkY, int chunkZ, boolean extrude, boolean initRemovedNibbles) {
        SkyLightEngine engine = this.engine;
        if (chunkY < LightEngineCache.MIN_LIGHT_SECTION || chunkY > LightEngineCache.MAX_LIGHT_SECTION
                || engine.getChunkInCache(chunkX, chunkZ) == null) {
            return;
        }
        SWMRNibbleArray nibble = engine.getNibbleFromCache(chunkX, chunkY, chunkZ);
        if (nibble == null) {
            if (!initRemovedNibbles) {
                return;
            }
            nibble = new SWMRNibbleArray(null, true);
            engine.setNibbleInCache(chunkX, chunkY, chunkZ, nibble);
        }
        initSkyNibble(nibble, chunkX, chunkY, chunkZ, extrude);
    }

    // Initializes the sky nibble.
    private void initSkyNibble(SWMRNibbleArray nibble, int chunkX, int chunkY, int chunkZ, boolean extrude) {
        SkyLightEngine engine = this.engine;
        if (!nibble.isNullNibbleUpdating()) {
            return;
        }
        if (chunkY > LightEngineCache.MAX_SECTION) {
            nibble.setFull();
            return;
        }
        if (chunkY < LightEngineCache.MIN_SECTION) {
            nibble.setNonNull();
            nibble.setZero();
            return;
        }

        boolean[] emptinessMap = engine.getEmptinessMap(chunkX, chunkZ);
        int lowestNonEmpty = LightEngineCache.MIN_SECTION - 1;
        for (int sectionY = LightEngineCache.MAX_SECTION; sectionY >= LightEngineCache.MIN_SECTION; --sectionY) {
            if (emptinessMap != null) {
                if (emptinessMap[sectionY]) {
                    continue;
                }
            } else {
                if (BfsLightEngine.isEmptyOfBlocks(engine.getChunkSection(chunkX, sectionY, chunkZ))) {
                    continue;
                }
            }
            lowestNonEmpty = sectionY;
            break;
        }

        if (chunkY > lowestNonEmpty) {
            nibble.setNonNull();
            nibble.setFull();
        } else if (extrude) {
            for (int currentY = chunkY + 1; currentY <= LightEngineCache.MAX_LIGHT_SECTION; ++currentY) {
                SWMRNibbleArray above = engine.getNibbleFromCache(chunkX, currentY, chunkZ);
                if (above != null && !above.isNullNibbleUpdating()) {
                    nibble.setNonNull();
                    nibble.extrudeLower(above);
                    break;
                }
            }
        } else {
            nibble.setNonNull();
        }
    }

    // Stores the nibble null.
    void setNibbleNull(int chunkX, int chunkY, int chunkZ) {
        SWMRNibbleArray nibble = this.engine.getNibbleFromCache(chunkX, chunkY, chunkZ);
        if (nibble != null) {
            nibble.setNull();
        }
    }

    // Reloads the cached nibbles.
    void rewriteNibbleCache() {
        SWMRNibbleArray[] cache = this.engine.nibbleCache;
        for (int index = 0; index < cache.length; ++index) {
            SWMRNibbleArray nibble = cache[index];
            if (nibble != null && nibble.isNullNibbleUpdating()) {
                cache[index] = null;
                nibble.updateVisible();
            }
        }
    }

    // Clears the null check flags.
    void resetNullPropagationChecks() {
        Arrays.fill(this.nullPropagationChecks, false);
    }

    // Prepares edge checks for a chunk.
    void prepareBatchedEdgeChecks(int chunkX, int chunkZ) {
        resetNullPropagationChecks();
        rewriteNibbleCache();
        for (int sectionY = LightEngineCache.MAX_LIGHT_SECTION; sectionY >= LightEngineCache.MIN_LIGHT_SECTION; --sectionY) {
            checkNullSection(chunkX, sectionY, chunkZ, true);
        }
    }

    // Initialises a null section and returns whether it changed.
    boolean checkNullSection(int chunkX, int chunkY, int chunkZ, boolean extrudeInitialised) {
        SkyLightEngine engine = this.engine;
        if (chunkY < LightEngineCache.MIN_LIGHT_SECTION || chunkY > LightEngineCache.MAX_LIGHT_SECTION
                || this.nullPropagationChecks[chunkY - LightEngineCache.MIN_LIGHT_SECTION]) {
            return false;
        }
        this.nullPropagationChecks[chunkY - LightEngineCache.MIN_LIGHT_SECTION] = true;

        boolean needInitNeighbours = false;
        neighbourSearch:
        for (int deltaZ = -1; deltaZ <= 1; ++deltaZ) {
            for (int deltaX = -1; deltaX <= 1; ++deltaX) {
                SWMRNibbleArray nibble = engine.getNibbleFromCache(chunkX + deltaX, chunkY, chunkZ + deltaZ);
                if (nibble != null && !nibble.isNullNibbleUpdating()) {
                    needInitNeighbours = true;
                    break neighbourSearch;
                }
            }
        }

        if (needInitNeighbours) {
            for (int deltaZ = -1; deltaZ <= 1; ++deltaZ) {
                for (int deltaX = -1; deltaX <= 1; ++deltaX) {
                    initNibble(chunkX + deltaX, chunkY, chunkZ + deltaZ, (deltaX | deltaZ) != 0 || extrudeInitialised, true);
                }
            }
        }
        return needInitNeighbours;
    }

    // Returns the light of a position with null sections extruded.
    private int getLightLevelExtruded(int worldX, int worldY, int worldZ) {
        SkyLightEngine engine = this.engine;
        int chunkX = worldX >> 4;
        int chunkY = worldY >> 4;
        int chunkZ = worldZ >> 4;

        int cacheIndex = chunkX + 5 * chunkZ + (5 * 5) * chunkY + engine.chunkSectionIndexOffset;
        if (engine.nibbleCache[cacheIndex] != null) {
            return engine.getLightLevel(cacheIndex, (worldX & 15) | ((worldZ & 15) << 4) | ((worldY & 15) << 8));
        }

        while (++chunkY <= LightEngineCache.MAX_LIGHT_SECTION) {
            int nextCacheIndex = chunkX + 5 * chunkZ + (5 * 5) * chunkY + engine.chunkSectionIndexOffset;
            if (engine.nibbleCache[nextCacheIndex] != null) {
                return engine.getLightLevel(nextCacheIndex, (worldX & 15) | ((worldZ & 15) << 4));
            }
        }
        return 15;
    }

    // Tries the propagate skylight.
    int tryPropagateSkylight(int worldX, int startY, int worldZ, boolean extrudeInitialised, boolean delayLightSet) {
        SkyLightEngine engine = this.engine;
        int encodeOffset = engine.coordinateOffset;

        int extrudedLevel = getLightLevelExtruded(worldX, startY + 1, worldZ);
        if (extrudedLevel == 0) {
            return startY;
        }

        checkNullSection(worldX >> 4, startY >> 4, worldZ >> 4, extrudeInitialised);
        int currentSky = extrudedLevel;
        int aboveInfo = engine.lightInfoAt(engine.getBlockState(worldX, startY + 1, worldZ), worldX, startY + 1, worldZ);

        for (; startY >= (LightEngineCache.MIN_LIGHT_SECTION << 4); --startY) {
            if ((startY & 15) == 15) {
                checkNullSection(worldX >> 4, startY >> 4, worldZ >> 4, extrudeInitialised);
            }
            int currentInfo = engine.lightInfoAt(engine.getBlockState(worldX, startY, worldZ), worldX, startY, worldZ);

            int aboveOpacity = LightInfo.opacity(aboveInfo);
            if (aboveOpacity > 0 && ((aboveInfo & LightInfo.REGISTRY) == 0 || LightInfo.isFaceSolid(aboveInfo, 5))) {
                break;
            }

            int currentOpacity = LightInfo.opacity(currentInfo);
            if (currentOpacity > 0 && ((currentInfo & LightInfo.REGISTRY) == 0 || LightInfo.isFaceSolid(currentInfo, 4))) {
                break;
            }
            if (currentOpacity > 0) {
                currentSky -= currentOpacity;
                if (currentSky <= 0) {
                    break;
                }
            }

            long speculativeValue = BfsLightEngine.encodeCoords(worldX, worldZ, startY, encodeOffset)
                    | engine.encodeQueueLevel(currentSky)
                    | (COLUMN_SPREAD << BfsLightEngine.DIRECTION_SHIFT)
                    | BfsLightEngine.sidedFlag(currentInfo);
            boolean appended = engine.appendToIncreaseQueue(speculativeValue);

            if (engine.getNibbleFromCache(worldX >> 4, startY >> 4, worldZ >> 4) == null) {
                if (appended) {
                    --engine.increaseQueueInitialLength;
                }
                startY &= ~15;
                aboveInfo = LightInfo.of(LightEngineCache.AIR);
            } else {
                if (!delayLightSet) {
                    engine.setLightLevel(worldX, startY, worldZ, currentSky);
                }
                aboveInfo = currentInfo;
            }
        }
        return startY;
    }

    // Queues the sky light changes of changed positions.
    void processBlockPositionChanges(int chunkX, int chunkZ, IntOpenHashSet changedPositions) {
        SkyLightEngine engine = this.engine;
        rewriteNibbleCache();
        resetNullPropagationChecks();

        int[] columnMaxY = this.columnMaxY;
        Arrays.fill(columnMaxY, Integer.MIN_VALUE);

        IntIterator iterator = changedPositions.iterator();
        while (iterator.hasNext()) {
            int packed = iterator.nextInt();
            int worldY = packed >> 8;
            if (worldY < 0 || worldY > 255) {
                continue;
            }
            int column = packed & 255;
            if (worldY > columnMaxY[column]) {
                columnMaxY[column] = worldY;
            }
        }

        for (int column = 0; column < 256; ++column) {
            int maximumY = columnMaxY[column];
            if (maximumY == Integer.MIN_VALUE) {
                continue;
            }
            int worldX = (chunkX << 4) | (column & 15);
            int worldZ = (chunkZ << 4) | (column >> 4);
            seedColumn(worldX, maximumY, worldZ);
        }

        applyDelayedQueues();

        iterator = changedPositions.iterator();
        while (iterator.hasNext()) {
            int packed = iterator.nextInt();
            int worldY = packed >> 8;
            if (worldY < 0 || worldY > 255) {
                continue;
            }
            ++engine.lastPositionsProcessed;
            engine.checkBlock((chunkX << 4) | (packed & 15), worldY, (chunkZ << 4) | ((packed >> 4) & 15));
        }
        engine.performLightDecrease();
    }

    // Queues the sky light change of one block.
    void propagateBlockChange(int blockX, int blockY, int blockZ) {
        SkyLightEngine engine = this.engine;
        rewriteNibbleCache();
        resetNullPropagationChecks();

        seedColumn(blockX, blockY, blockZ);
        applyDelayedQueues();
        engine.checkBlock(blockX, blockY, blockZ);
        engine.performLightDecrease();
    }

    // Queues full sky light down a column.
    private void seedColumn(int worldX, int fromY, int worldZ) {
        int maximumPropagationY = tryPropagateSkylight(worldX, fromY, worldZ, true, true);
        seedFullColumnDecrease(worldX, maximumPropagationY, worldZ);
    }

    // Applies the delayed queues.
    private void applyDelayedQueues() {
        SkyLightEngine engine = this.engine;
        applyDelayedQueue(engine.increaseQueue, engine.increaseQueueInitialLength, true);
        applyDelayedQueue(engine.decreaseQueue, engine.decreaseQueueInitialLength, false);
    }

    // Queues a decrease down a whole column.
    private void seedFullColumnDecrease(int worldX, int maximumPropagationY, int worldZ) {
        SkyLightEngine engine = this.engine;
        if (getLightLevelExtruded(worldX, maximumPropagationY, worldZ) != 15) {
            return;
        }
        int encodeOffset = engine.coordinateOffset;
        checkNullSection(worldX >> 4, maximumPropagationY >> 4, worldZ >> 4, true);
        for (int currentY = maximumPropagationY; currentY >= (LightEngineCache.MIN_LIGHT_SECTION << 4); --currentY) {
            if ((currentY & 15) == 15) {
                checkNullSection(worldX >> 4, currentY >> 4, worldZ >> 4, true);
            }
            SWMRNibbleArray nibble = engine.nibbleCache[(worldX >> 4) + 5 * (worldZ >> 4) + (5 * 5) * (currentY >> 4) + engine.chunkSectionIndexOffset];
            if (nibble == null) {
                currentY &= ~15;
                continue;
            }
            if (engine.getLightLevel(worldX, currentY, worldZ) != 15) {
                break;
            }
            engine.appendToDecreaseQueue(BfsLightEngine.encodeCoords(worldX, worldZ, currentY, encodeOffset)
                    | engine.encodeQueueLevel(15)
                    | (COLUMN_SPREAD << BfsLightEngine.DIRECTION_SHIFT));
        }
    }

    // Applies the delayed queue.
    private void applyDelayedQueue(long[] queue, int length, boolean useLevel) {
        SkyLightEngine engine = this.engine;
        int decodeOffsetX = -engine.encodeOffsetX;
        int decodeOffsetY = -engine.encodeOffsetY;
        int decodeOffsetZ = -engine.encodeOffsetZ;
        for (int index = 0; index < length; ++index) {
            long queueValue = queue[index];
            int positionX = ((int) queueValue & 63) + decodeOffsetX;
            int positionZ = (((int) queueValue >>> 6) & 63) + decodeOffsetZ;
            int positionY = (((int) queueValue >>> 12) & 0xFFFF) + decodeOffsetY;
            int level = useLevel ? (int) ((queueValue >>> BfsLightEngine.LIGHT_LEVEL_SHIFT) & 15) : 0;
            engine.setLightLevel(positionX, positionY, positionZ, level);
        }
    }
}
