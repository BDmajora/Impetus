package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.async.AsyncLitChunk;
import com.bdmajora.fulgor.async.SWMRNibbleArray;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

// Light engine of block light.
public class BlockLightEngine extends BfsLightEngine {
    // Creates a block light engine from its world.
    public BlockLightEngine(World world) {
        super(false, world);
    }

    // Returns the emptiness map.
    @Override
    protected boolean[] getEmptinessMap(Chunk chunk) {
        return ((AsyncLitChunk) chunk).fulgor$getBlockEmptinessMap();
    }

    // Stores the emptiness map.
    @Override
    protected void setEmptinessMap(Chunk chunk, boolean[] to) {
        ((AsyncLitChunk) chunk).fulgor$setBlockEmptinessMap(to);
    }

    // Returns the block light nibbles of a chunk.
    @Override
    protected SWMRNibbleArray[] getNibblesOnChunk(Chunk chunk) {
        return ((AsyncLitChunk) chunk).fulgor$getBlockNibbles();
    }

    // Stores the nibbles.
    @Override
    protected void setNibbles(Chunk chunk, SWMRNibbleArray[] to) {
        ((AsyncLitChunk) chunk).fulgor$setBlockNibbles(to);
    }

    // Returns the vanilla block light array of a section.
    @Override
    protected NibbleArray vanillaLightArray(ExtendedBlockStorage section) {
        return section.getBlockLight();
    }

    // Initializes the nibble.
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

    // Stores the nibble null.
    @Override
    protected void setNibbleNull(int chunkX, int chunkY, int chunkZ) {
        SWMRNibbleArray nib = this.nibbleCache[chunkX + 5 * chunkZ + (5 * 5) * chunkY + this.chunkSectionIndexOffset];
        if (nib != null) {
            nib.setHidden();
        }
    }

    // Queues a recheck of one block.
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

        appendToDecreaseQueue(encodeCoords(worldX, worldZ, worldY, encodeOffset)
                | encodeQueueLevel(currentLevel)
                | (((long) ALL_DIRECTIONS_BITSET) << DIRECTION_SHIFT));
    }

    // Returns the block light a block should have.
    @Override
    protected int calculateLightValue(int worldX, int worldY, int worldZ, int expect) {
        int info = lightInfoAt(getBlockState(worldX, worldY, worldZ), worldX, worldY, worldZ);
        int level = LightInfo.emission(info);

        if (level >= 14 || level > expect) {
            return level;
        }

        return attenuateFromNeighbours(worldX, worldY, worldZ, expect, info, level);
    }

    // Queues the light changes of one changed block.
    @Override
    protected void propagateBlockChanges(Chunk atChunk, int blockX, int blockY, int blockZ) {
        checkBlock(blockX, blockY, blockZ);
        performLightDecrease();
    }

    // Lights a chunk from its emitting blocks.
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
}
