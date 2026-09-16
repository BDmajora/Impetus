package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.async.AsyncLitChunk;
import com.bdmajora.fulgor.async.SWMRNibbleArray;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

// Block-light lane: BFS from emitters with per-block emission and per-face opacity, reading the packed LightInfo instead of making virtual calls per visit
public class BlockLightEngine extends BfsLightEngine {
    public BlockLightEngine(World world) {
        super(false, world);
    }

    @Override
    protected boolean[] getEmptinessMap(Chunk chunk) {
        return ((AsyncLitChunk) chunk).fulgor$getBlockEmptinessMap();
    }

    @Override
    protected void setEmptinessMap(Chunk chunk, boolean[] to) {
        ((AsyncLitChunk) chunk).fulgor$setBlockEmptinessMap(to);
    }

    @Override
    protected SWMRNibbleArray[] getNibblesOnChunk(Chunk chunk) {
        return ((AsyncLitChunk) chunk).fulgor$getBlockNibbles();
    }

    @Override
    protected void setNibbles(Chunk chunk, SWMRNibbleArray[] to) {
        ((AsyncLitChunk) chunk).fulgor$setBlockNibbles(to);
    }

    @Override
    protected boolean canUseChunk(Chunk chunk) {
        return ((AsyncLitChunk) chunk).fulgor$isLightUsable();
    }

    @Override
    protected void initNibble(int chunkX, int chunkY, int chunkZ, boolean extrude, boolean initRemovedNibbles) {
        if (chunkY < MIN_LIGHT_SECTION || chunkY > MAX_LIGHT_SECTION || getChunkInCache(chunkX, chunkZ) == null) {
            return;
        }
        SWMRNibbleArray nib = this.nibbleCache[chunkX + 5 * chunkZ + (5 * 5) * chunkY + this.chunkSectionIndexOffset];
        if (nib == null) {
            if (!initRemovedNibbles) {
                return;
            }
            setNibbleInCache(chunkX, chunkY, chunkZ, new SWMRNibbleArray());
        } else {
            nib.setNonNull();
        }
    }

    // Hidden rather than null so the data survives for a decrease still propagating through it
    @Override
    protected void setNibbleNull(int chunkX, int chunkY, int chunkZ) {
        SWMRNibbleArray nib = this.nibbleCache[chunkX + 5 * chunkZ + (5 * 5) * chunkY + this.chunkSectionIndexOffset];
        if (nib != null) {
            nib.setHidden();
        }
    }

    // No early-out on an unchanged level: a slab swap can keep the cell's level while flipping which faces it feeds
    @Override
    protected void checkBlock(int worldX, int worldY, int worldZ) {
        int encodeOffset = this.coordinateOffset;
        int currentLevel = getLightLevel(worldX, worldY, worldZ);

        IBlockState state = getBlockState(worldX, worldY, worldZ);
        int info = lightInfoAt(state, worldX, worldY, worldZ);
        int emission = LightInfo.emission(info);

        setLightLevel(worldX, worldY, worldZ, emission);

        if (emission > 0) {
            appendToIncreaseQueue(encodeCoords(worldX, worldZ, worldY, encodeOffset)
                    | encodeQueueLevel(emission)
                    | (((long) ALL_DIRECTIONS_BITSET) << DIRECTION_SHIFT)
                    | sidedFlag(info));
        }

        // No sided flag on the decrease: the light being erased flowed through the old block, not the new one's faces
        appendToDecreaseQueue(encodeCoords(worldX, worldZ, worldY, encodeOffset)
                | encodeQueueLevel(currentLevel)
                | (((long) ALL_DIRECTIONS_BITSET) << DIRECTION_SHIFT));
    }

    @Override
    protected int calculateLightValue(int worldX, int worldY, int worldZ, int expect) {
        return calculateLightValueWithInfo(worldX, worldY, worldZ, expect,
                lightInfoAt(getBlockState(worldX, worldY, worldZ), worldX, worldY, worldZ));
    }

    private int calculateLightValueWithInfo(int worldX, int worldY, int worldZ, int expect, int info) {
        int level = LightInfo.emission(info);

        if (level >= 14 || level > expect) {
            return level;
        }

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

    @Override
    protected void propagateBlockChanges(Chunk atChunk, int blockX, int blockY, int blockZ) {
        checkBlock(blockX, blockY, blockZ);
        performLightDecrease();
    }

    // Every changed position is seeded before one drain, so a dense edit is not re-flooded once per block
    @Override
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

    @Override
    protected void lightChunk(Chunk chunk, boolean needsEdgeChecks) {
        int offX = chunk.x << 4;
        int offZ = chunk.z << 4;
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();

        for (int sectionY = MIN_SECTION; sectionY <= MAX_SECTION; ++sectionY) {
            if (isEmptyOfBlocks(sections[sectionY])) {
                continue;
            }

            int offY = sectionY << 4;
            int sectionIdx = chunk.x + 5 * chunk.z + (5 * 5) * sectionY + this.chunkSectionIndexOffset;

            for (int index = 0; index < (16 * 16 * 16); ++index) {
                int lx = index & 15;
                int ly = index >>> 8;
                int lz = (index >>> 4) & 15;

                int worldX = offX | lx;
                int worldY = offY | ly;
                int worldZ = offZ | lz;

                IBlockState state = getBlockStateFast(sectionIdx, lx, ly, lz);
                int info = lightInfoAt(state, worldX, worldY, worldZ);
                int emission = LightInfo.emission(info);
                if (emission <= 0) {
                    continue;
                }

                int currentLevel = getLightLevel(worldX, worldY, worldZ);
                if (emission <= currentLevel) {
                    continue;
                }

                appendToIncreaseQueue(encodeCoords(worldX, worldZ, worldY, this.coordinateOffset)
                        | encodeQueueLevel(emission)
                        | (((long) ALL_DIRECTIONS_BITSET) << DIRECTION_SHIFT)
                        | sidedFlag(info));

                setLightLevel(worldX, worldY, worldZ, emission);
            }
        }

        if (needsEdgeChecks) {
            performLightIncrease();
            checkChunkEdges(chunk, MIN_LIGHT_SECTION, MAX_LIGHT_SECTION);
        } else {
            propagateNeighbourLevels(chunk, MIN_LIGHT_SECTION, MAX_LIGHT_SECTION);
            performLightIncrease();
        }
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

                // Independent of the re-seed: the decrease keeps walking past emitters, or removing a bright source leaves a ghost region behind any dimmer one
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

    // Mirrors the published section into the vanilla block-light array; a no-op on the client where the SWMR array shares the vanilla storage
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
        NibbleArray vanilla = section.getBlockLight();
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
