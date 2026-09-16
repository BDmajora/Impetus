package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.async.AsyncLitChunk;
import com.bdmajora.fulgor.async.SWMRNibbleArray;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

// Sky-light lane: vertical extrusion seeds full light down from the top of the world, then the same horizontal BFS as block light attenuates it through partial occluders
public class SkyLightEngine extends BfsLightEngine {
    private final SkyLightColumnProcessor columnProcessor;

    public SkyLightEngine(World world) {
        super(true, world);
        this.columnProcessor = new SkyLightColumnProcessor(this);
    }

    @Override
    protected boolean[] getEmptinessMap(Chunk chunk) {
        return ((AsyncLitChunk) chunk).fulgor$getSkyEmptinessMap();
    }

    @Override
    protected void setEmptinessMap(Chunk chunk, boolean[] to) {
        ((AsyncLitChunk) chunk).fulgor$setSkyEmptinessMap(to);
    }

    @Override
    protected SWMRNibbleArray[] getNibblesOnChunk(Chunk chunk) {
        return ((AsyncLitChunk) chunk).fulgor$getSkyNibbles();
    }

    @Override
    protected void setNibbles(Chunk chunk, SWMRNibbleArray[] to) {
        ((AsyncLitChunk) chunk).fulgor$setSkyNibbles(to);
    }

    @Override
    protected boolean canUseChunk(Chunk chunk) {
        return ((AsyncLitChunk) chunk).fulgor$isLightUsable();
    }

    @Override
    protected void initNibble(int chunkX, int chunkY, int chunkZ, boolean extrude, boolean initRemovedNibbles) {
        this.columnProcessor.initNibble(chunkX, chunkY, chunkZ, extrude, initRemovedNibbles);
    }

    @Override
    protected void setNibbleNull(int chunkX, int chunkY, int chunkZ) {
        this.columnProcessor.setNibbleNull(chunkX, chunkY, chunkZ);
    }

    @Override
    protected void prepareBatchedEdgeChecks(int chunkX, int chunkZ) {
        this.columnProcessor.prepareBatchedEdgeChecks(chunkX, chunkZ);
    }

    // One column walk per changed column from its highest change, every position seeded into the same queues, one drain; the per-position path re-flooded overlapping regions once per block on a dense edit
    @Override
    protected void processBlockPositionChanges(Chunk chunk, int chunkX, int chunkZ, IntOpenHashSet changedPositions) {
        this.columnProcessor.processBlockPositionChanges(chunkX, chunkZ, changedPositions);
    }

    @Override
    protected void propagateBlockChanges(Chunk atChunk, int blockX, int blockY, int blockZ) {
        this.columnProcessor.propagateBlockChange(blockX, blockY, blockZ);
    }

    @Override
    protected void checkBlock(int worldX, int worldY, int worldZ) {
        int currentLevel = getLightLevel(worldX, worldY, worldZ);
        int encodeOffset = this.coordinateOffset;

        if (currentLevel == 15) {
            // A clobbered source must re-propagate; the sided flag is set since it is not known whether the block is conditionally transparent
            appendToIncreaseQueue(encodeCoords(worldX, worldZ, worldY, encodeOffset)
                    | encodeQueueLevel(15)
                    | (((long) ALL_DIRECTIONS_BITSET) << DIRECTION_SHIFT)
                    | FLAG_HAS_SIDED_TRANSPARENT_BLOCKS);
        } else {
            setLightLevel(worldX, worldY, worldZ, 0);
        }

        // Unconditional: the decrease both removes stale light and, through its brighter-neighbour branch, re-seeds the flood from adjacent sky; skipping it for sources left newly revealed cavities black
        appendToDecreaseQueue(encodeCoords(worldX, worldZ, worldY, encodeOffset)
                | encodeQueueLevel(currentLevel)
                | (((long) ALL_DIRECTIONS_BITSET) << DIRECTION_SHIFT));
    }

    @Override
    protected int calculateLightValue(int worldX, int worldY, int worldZ, int expect) {
        if (expect == 15) {
            return expect;
        }

        int sectionOffset = this.chunkSectionIndexOffset;
        int info = lightInfoAt(getBlockState(worldX, worldY, worldZ), worldX, worldY, worldZ);
        int rawOpacity = LightInfo.opacity(info);
        boolean sidedTransparent = rawOpacity > 1 && (info & LightInfo.REGISTRY) != 0;
        int faceBits = LightInfo.faceBits(info);
        int uniformAbsorption = !sidedTransparent ? Math.max(1, rawOpacity) : 0;

        int level = 0;
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

    @Override
    protected void lightChunk(Chunk chunk, boolean needsEdgeChecks) {
        this.columnProcessor.rewriteNibbleCache();
        this.columnProcessor.resetNullPropagationChecks();

        int chunkX = chunk.x;
        int chunkZ = chunk.z;
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();

        // Empty sections at the top are full sky; their neighbours' edge columns are seeded so light spills sideways into them
        int highestNonEmptySection = MAX_SECTION;
        while (highestNonEmptySection >= MIN_SECTION && isEmptyOfBlocks(sections[highestNonEmptySection])) {
            this.columnProcessor.checkNullSection(chunkX, highestNonEmptySection, chunkZ, false);

            for (AxisDirection direction : ONLY_HORIZONTAL_DIRECTIONS) {
                int neighbourX = chunkX + direction.x;
                int neighbourZ = chunkZ + direction.z;
                SWMRNibbleArray neighbourNibble = getNibbleFromCache(neighbourX, highestNonEmptySection, neighbourZ);
                if (neighbourNibble == null) {
                    continue;
                }

                int incX, incZ, startX, startZ;
                if (direction.x != 0) {
                    incX = 0;
                    incZ = 1;
                    startX = direction.x < 0 ? chunkX << 4 : chunkX << 4 | 15;
                    startZ = chunkZ << 4;
                } else {
                    incX = 1;
                    incZ = 0;
                    startZ = direction.z < 0 ? chunkZ << 4 : chunkZ << 4 | 15;
                    startX = chunkX << 4;
                }

                int encodeOffset = this.coordinateOffset;
                long propagateDir = 1L << direction.ordinal();

                for (int currY = highestNonEmptySection << 4, maxY = currY | 15; currY <= maxY; ++currY) {
                    for (int i = 0, currX = startX, currZ = startZ; i < 16; ++i, currX += incX, currZ += incZ) {
                        appendToIncreaseQueue(encodeCoords(currX, currZ, currY, encodeOffset)
                                | encodeQueueLevel(15)
                                | (propagateDir << DIRECTION_SHIFT));
                    }
                }
            }

            --highestNonEmptySection;
        }

        if (highestNonEmptySection >= MIN_SECTION) {
            int minX = chunkX << 4;
            int maxX = chunkX << 4 | 15;
            int minZ = chunkZ << 4;
            int maxZ = chunkZ << 4 | 15;
            int startY = highestNonEmptySection << 4 | 15;
            for (int currZ = minZ; currZ <= maxZ; ++currZ) {
                for (int currX = minX; currX <= maxX; ++currX) {
                    this.columnProcessor.tryPropagateSkylight(currX, startY + 1, currZ, false, false);
                }
            }
        }

        if (needsEdgeChecks) {
            performLightIncrease();
            for (int y = highestNonEmptySection; y >= MIN_LIGHT_SECTION; --y) {
                this.columnProcessor.checkNullSection(chunkX, y, chunkZ, false);
            }
            super.checkChunkEdges(chunk, MIN_LIGHT_SECTION, highestNonEmptySection);
        } else {
            for (int y = highestNonEmptySection; y >= MIN_LIGHT_SECTION; --y) {
                this.columnProcessor.checkNullSection(chunkX, y, chunkZ, false);
            }
            propagateNeighbourLevels(chunk, MIN_LIGHT_SECTION, highestNonEmptySection);
            performLightIncrease();
        }
    }

    @Override
    protected void checkChunkEdges(Chunk chunk, int fromSection, int toSection) {
        this.columnProcessor.resetNullPropagationChecks();
        this.columnProcessor.rewriteNibbleCache();

        int chunkX = chunk.x;
        int chunkZ = chunk.z;
        for (int y = toSection; y >= fromSection; --y) {
            this.columnProcessor.checkNullSection(chunkX, y, chunkZ, true);
        }
        super.checkChunkEdges(chunk, fromSection, toSection);
    }

    @Override
    protected void performLightIncrease() {
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

            boolean hasSidedTransparent = (queueValue & FLAG_HAS_SIDED_TRANSPARENT_BLOCKS) != 0L;
            int srcBlockedFaces = 0;
            if (hasSidedTransparent) {
                int srcIdx = (posX >> 4) + 5 * (posZ >> 4) + (5 * 5) * (posY >> 4) + sectionOffset;
                IBlockState srcState = getBlockStateFast(srcIdx, posX & 15, posY & 15, posZ & 15);
                srcBlockedFaces = LightInfo.faceBits(lightInfoAt(srcState, posX, posY, posZ));
            }

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

    @Override
    protected void performLightDecrease() {
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

        while (queueReadIndex < queueLength) {
            long queueValue = queue[queueReadIndex++];

            int posX = ((int) queueValue & 63) + decodeOffsetX;
            int posZ = (((int) queueValue >>> 6) & 63) + decodeOffsetZ;
            int posY = (((int) queueValue >>> 12) & COORD_Y_MASK) + decodeOffsetY;
            int propagatedLevel = (int) ((queueValue >>> LIGHT_LEVEL_SHIFT) & 0xF);
            AxisDirection[] checkDirections = OLD_CHECK_DIRECTIONS[(int) ((queueValue >>> DIRECTION_SHIFT) & 63)];

            boolean hasSidedTransparent = (queueValue & FLAG_HAS_SIDED_TRANSPARENT_BLOCKS) != 0L;
            int srcBlockedFaces = 0;
            if (hasSidedTransparent) {
                int srcIdx = (posX >> 4) + 5 * (posZ >> 4) + (5 * 5) * (posY >> 4) + sectionOffset;
                IBlockState srcState = getBlockStateFast(srcIdx, posX & 15, posY & 15, posZ & 15);
                srcBlockedFaces = LightInfo.faceBits(lightInfoAt(srcState, posX, posY, posZ));
            }

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

                // The level-1 boundary entries re-flood the cleared region's outer ring from the surrounding light
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

    // Mirrors the published section into the vanilla skylight array; a no-op on the client where the SWMR array shares the vanilla storage
    @Override
    protected void onNibbleVisible(int cacheIndex, SWMRNibbleArray nibble) {
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
        NibbleArray vanilla = section.getSkyLight();
        if (vanilla == null) {
            return;
        }
        byte[] dst = vanilla.getData();
        if (dst == srcData) {
            return;
        }
        System.arraycopy(srcData, 0, dst, 0, srcData.length);
    }
}
