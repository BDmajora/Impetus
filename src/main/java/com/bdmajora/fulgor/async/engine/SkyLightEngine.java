package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.async.AsyncLitChunk;
import com.bdmajora.fulgor.async.SWMRNibbleArray;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

// Light engine of sky light.
public class SkyLightEngine extends BfsLightEngine {
    // Processor of sky columns.
    private final SkyLightColumnProcessor columnProcessor;

    // Creates a sky light engine from its world.
    public SkyLightEngine(World world) {
        super(true, world);
        this.columnProcessor = new SkyLightColumnProcessor(this);
    }

    // Returns the emptiness map.
    @Override
    protected boolean[] getEmptinessMap(Chunk chunk) {
        return ((AsyncLitChunk) chunk).fulgor$getSkyEmptinessMap();
    }

    // Stores the emptiness map.
    @Override
    protected void setEmptinessMap(Chunk chunk, boolean[] to) {
        ((AsyncLitChunk) chunk).fulgor$setSkyEmptinessMap(to);
    }

    // Returns the sky light nibbles of a chunk.
    @Override
    protected SWMRNibbleArray[] getNibblesOnChunk(Chunk chunk) {
        return ((AsyncLitChunk) chunk).fulgor$getSkyNibbles();
    }

    // Stores the nibbles.
    @Override
    protected void setNibbles(Chunk chunk, SWMRNibbleArray[] to) {
        ((AsyncLitChunk) chunk).fulgor$setSkyNibbles(to);
    }

    // Returns the vanilla sky light array of a section.
    @Override
    protected NibbleArray vanillaLightArray(ExtendedBlockStorage section) {
        return section.getSkyLight();
    }

    // Initializes the nibble.
    @Override
    protected void initNibble(int chunkX, int chunkY, int chunkZ, boolean extrude, boolean initRemovedNibbles) {
        this.columnProcessor.initNibble(chunkX, chunkY, chunkZ, extrude, initRemovedNibbles);
    }

    // Stores the nibble null.
    @Override
    protected void setNibbleNull(int chunkX, int chunkY, int chunkZ) {
        this.columnProcessor.setNibbleNull(chunkX, chunkY, chunkZ);
    }

    // Prepares edge checks for a chunk.
    @Override
    protected void prepareBatchedEdgeChecks(int chunkX, int chunkZ) {
        this.columnProcessor.prepareBatchedEdgeChecks(chunkX, chunkZ);
    }

    // Queues the sky light changes of changed positions.
    @Override
    protected void processBlockPositionChanges(Chunk chunk, int chunkX, int chunkZ, IntOpenHashSet changedPositions) {
        this.columnProcessor.processBlockPositionChanges(chunkX, chunkZ, changedPositions);
    }

    // Queues the light changes of one changed block.
    @Override
    protected void propagateBlockChanges(Chunk atChunk, int blockX, int blockY, int blockZ) {
        this.columnProcessor.propagateBlockChange(blockX, blockY, blockZ);
    }

    // Queues a recheck of one block.
    @Override
    protected void checkBlock(int worldX, int worldY, int worldZ) {
        int currentLevel = getLightLevel(worldX, worldY, worldZ);
        int encodeOffset = this.coordinateOffset;

        if (currentLevel == 15) {
            appendToIncreaseQueue(encodeCoords(worldX, worldZ, worldY, encodeOffset)
                    | encodeQueueLevel(15)
                    | (((long) ALL_DIRECTIONS_BITSET) << DIRECTION_SHIFT)
                    | FLAG_HAS_SIDED_TRANSPARENT_BLOCKS);
        } else {
            setLightLevel(worldX, worldY, worldZ, 0);
        }

        appendToDecreaseQueue(encodeCoords(worldX, worldZ, worldY, encodeOffset)
                | encodeQueueLevel(currentLevel)
                | (((long) ALL_DIRECTIONS_BITSET) << DIRECTION_SHIFT));
    }

    // Returns the sky light a block should have.
    @Override
    protected int calculateLightValue(int worldX, int worldY, int worldZ, int expect) {
        if (expect == 15) {
            return expect;
        }
        IBlockState state = getBlockState(worldX, worldY, worldZ);
        int info = lightInfoAt(state, worldX, worldY, worldZ);
        return attenuateFromNeighbours(worldX, worldY, worldZ, expect, state, info, 0);
    }

    // Lights a chunk from the sky down.
    @Override
    protected void lightChunk(Chunk chunk, boolean needsEdgeChecks) {
        this.columnProcessor.rewriteNibbleCache();
        this.columnProcessor.resetNullPropagationChecks();

        int chunkX = chunk.x;
        int chunkZ = chunk.z;
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();

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

    // Checks the edges of a section range.
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
