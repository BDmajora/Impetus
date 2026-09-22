package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.async.AsyncLitChunk;
import com.bdmajora.fulgor.async.SWMRNibbleArray;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
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
    protected NibbleArray vanillaLightArray(ExtendedBlockStorage section) {
        return section.getSkyLight();
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
        int info = lightInfoAt(getBlockState(worldX, worldY, worldZ), worldX, worldY, worldZ);
        return attenuateFromNeighbours(worldX, worldY, worldZ, expect, info, 0);
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

                int incX = direction.edgeStepX;
                int incZ = direction.edgeStepZ;
                int startX = direction.edgeStartX(chunkX, false);
                int startZ = direction.edgeStartZ(chunkZ, false);
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
}
