package com.bdmajora.fulgor.async.engine;

import com.bdmajora.fulgor.Fulgor;
import com.bdmajora.fulgor.async.AsyncLitWorld;
import com.bdmajora.fulgor.async.ChunkLightHelper;
import com.bdmajora.fulgor.async.RenderBounds;
import com.bdmajora.fulgor.async.SWMRNibbleArray;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.util.Arrays;

// Cache of chunks and sections used by a light engine.
abstract class LightEngineCache {
    // Lowest block section.
    static final int MIN_SECTION = 0;
    // Highest block section.
    static final int MAX_SECTION = 15;
    // Lowest light section.
    static final int MIN_LIGHT_SECTION = ChunkLightHelper.MIN_LIGHT_SECTION;
    // Highest light section.
    static final int MAX_LIGHT_SECTION = ChunkLightHelper.MAX_LIGHT_SECTION;
    // Sections per cached column.
    private static final int CACHE_SECTIONS = ChunkLightHelper.LIGHT_SECTIONS + 2;
    // Entries of the section caches.
    private static final int CACHE_SIZE = 5 * 5 * CACHE_SECTIONS;
    // Air block state.
    static final IBlockState AIR = Blocks.AIR.getDefaultState();

    // Cached block sections.
    protected final ExtendedBlockStorage[] sectionCache = new ExtendedBlockStorage[CACHE_SIZE];
    // Cached light nibbles.
    protected final SWMRNibbleArray[] nibbleCache = new SWMRNibbleArray[CACHE_SIZE];
    // Flags of sections that changed light.
    protected final boolean[] notifyUpdateCache = new boolean[CACHE_SIZE];
    // Changed block boxes of each section.
    protected final long[] notifyBoundsCache = new long[CACHE_SIZE];
    // Cached chunks.
    protected final Chunk[] chunkCache = new Chunk[5 * 5];
    // Cached emptiness maps.
    protected final boolean[][] emptinessMapCache = new boolean[5 * 5][];
    // Cached fluid capabilities.
    protected final Object[] fluidCapCache = new Object[5 * 5];
    // Flag set on the client.
    protected final boolean isClientSide;
    // World read by the engine.
    protected final World world;
    // Reusable position for contextual light.
    private final BlockPos.MutableBlockPos contextualLightPos = new BlockPos.MutableBlockPos();
    // X offset of packed coordinates.
    protected int encodeOffsetX;
    // Y offset of packed coordinates.
    protected int encodeOffsetY;
    // Z offset of packed coordinates.
    protected int encodeOffsetZ;
    // Offset of packed coordinates.
    protected int coordinateOffset;
    // X offset of cache chunks.
    protected int chunkOffsetX;
    // Y offset of cache sections.
    protected int chunkOffsetY;
    // Z offset of cache chunks.
    protected int chunkOffsetZ;
    // Offset of chunk cache indices.
    protected int chunkIndexOffset;
    // Offset of section cache indices.
    protected int chunkSectionIndexOffset;

    // Creates a light engine cache from its world.
    LightEngineCache(World world) {
        this.isClientSide = world.isRemote;
        this.world = world;
    }

    // Sets up the caches.
    protected final void setupCaches(int centerX, int centerY, int centerZ, boolean relaxed, boolean tryToLoadChunksFor2Radius) {
        resetTaskState();

        int centerChunkX = centerX >> 4;
        int centerChunkZ = centerZ >> 4;
        setupEncodeOffset(centerChunkX * 16 + 7, centerY, centerChunkZ * 16 + 7);

        int radius = tryToLoadChunksFor2Radius ? 2 : 1;
        for (int deltaZ = -radius; deltaZ <= radius; ++deltaZ) {
            for (int deltaX = -radius; deltaX <= radius; ++deltaX) {
                int chunkX = centerChunkX + deltaX;
                int chunkZ = centerChunkZ + deltaZ;
                boolean isTwoRadius = Math.max(Math.abs(deltaX), Math.abs(deltaZ)) == 2;
                Chunk chunk = ((AsyncLitWorld) this.world).fulgor$getAnyChunkImmediately(chunkX, chunkZ);

                if (chunk == null) {
                    if (relaxed | isTwoRadius) {
                        continue;
                    }
                    throw new IllegalArgumentException("Trying to propagate light update before 1 radius neighbours ready");
                }
                if (!canUseChunk(chunk)) {
                    continue;
                }

                setChunkInCache(chunkX, chunkZ, chunk);
                if (Fulgor.hasFluidloggedApi()) {
                    this.fluidCapCache[chunkX + 5 * chunkZ + this.chunkIndexOffset] = FluidLightBridge.capabilityOf(chunk);
                }
                setEmptinessMapCache(chunkX, chunkZ, getEmptinessMap(chunk));
                if (!isTwoRadius) {
                    setBlocksForChunkInCache(chunkX, chunkZ, chunk.getBlockStorageArray());
                    setNibblesForChunkInCache(chunkX, chunkZ, getNibblesOnChunk(chunk));
                }
            }
        }
    }

    // Sets up the encode offset.
    private void setupEncodeOffset(int centerX, int centerY, int centerZ) {
        this.encodeOffsetX = 31 - centerX;
        this.encodeOffsetY = (-(MIN_LIGHT_SECTION - 1) << 4);
        this.encodeOffsetZ = 31 - centerZ;
        this.coordinateOffset = this.encodeOffsetX + (this.encodeOffsetZ << 6) + (this.encodeOffsetY << 12);
        this.chunkOffsetX = 2 - (centerX >> 4);
        this.chunkOffsetY = -(MIN_LIGHT_SECTION - 1);
        this.chunkOffsetZ = 2 - (centerZ >> 4);
        this.chunkIndexOffset = this.chunkOffsetX + (5 * this.chunkOffsetZ);
        this.chunkSectionIndexOffset = this.chunkIndexOffset + ((5 * 5) * this.chunkOffsetY);
    }

    // Returns a cached chunk or null.
    protected final Chunk getChunkInCache(int chunkX, int chunkZ) {
        return this.chunkCache[chunkX + 5 * chunkZ + this.chunkIndexOffset];
    }

    // Stores the chunk in cache.
    protected final void setChunkInCache(int chunkX, int chunkZ, Chunk chunk) {
        this.chunkCache[chunkX + 5 * chunkZ + this.chunkIndexOffset] = chunk;
    }

    // Returns the packed light info of a state at a position.
    protected final int lightInfoAt(IBlockState state, int worldX, int worldY, int worldZ) {
        int info = LightInfo.of(state);
        if (LightInfo.hasContextualValues(info)) {
            info = LightInfo.resolveContextual(info, state, this.world, this.contextualLightPos, worldX, worldY, worldZ);
        }
        if (!Fulgor.hasFluidloggedApi()) {
            return info;
        }
        Object capability = this.fluidCapCache[(worldX >> 4) + 5 * (worldZ >> 4) + this.chunkIndexOffset];
        return capability == null ? info
                : FluidLightBridge.merge(info, capability, worldX, worldY, worldZ, this.world, this.contextualLightPos);
    }

    // Returns a cached section or null.
    protected final ExtendedBlockStorage getChunkSection(int chunkX, int chunkY, int chunkZ) {
        return this.sectionCache[chunkX + 5 * chunkZ + (5 * 5) * chunkY + this.chunkSectionIndexOffset];
    }

    // Stores the blocks for chunk in cache.
    protected final void setBlocksForChunkInCache(int chunkX, int chunkZ, ExtendedBlockStorage[] sections) {
        for (int sectionY = MIN_LIGHT_SECTION; sectionY <= MAX_LIGHT_SECTION; ++sectionY) {
            ExtendedBlockStorage section = sections == null || sectionY < MIN_SECTION || sectionY > MAX_SECTION
                    ? null : sections[sectionY];
            this.sectionCache[chunkX + 5 * chunkZ + 5 * 5 * sectionY + this.chunkSectionIndexOffset] = section;
        }
    }

    // Returns a cached nibble array or null.
    protected final SWMRNibbleArray getNibbleFromCache(int chunkX, int chunkY, int chunkZ) {
        return this.nibbleCache[chunkX + 5 * chunkZ + (5 * 5) * chunkY + this.chunkSectionIndexOffset];
    }

    // Stores the nibble in cache.
    protected final void setNibbleInCache(int chunkX, int chunkY, int chunkZ, SWMRNibbleArray nibble) {
        this.nibbleCache[chunkX + 5 * chunkZ + (5 * 5) * chunkY + this.chunkSectionIndexOffset] = nibble;
    }

    // Stores the nibbles for chunk in cache.
    protected final void setNibblesForChunkInCache(int chunkX, int chunkZ, SWMRNibbleArray[] nibbles) {
        for (int sectionY = MIN_LIGHT_SECTION; sectionY <= MAX_LIGHT_SECTION; ++sectionY) {
            setNibbleInCache(chunkX, sectionY, chunkZ, nibbles == null ? null : nibbles[sectionY - MIN_LIGHT_SECTION]);
        }
    }

    // Returns the cached emptiness map of a chunk.
    protected final boolean[] getEmptinessMap(int chunkX, int chunkZ) {
        return this.emptinessMapCache[chunkX + 5 * chunkZ + this.chunkIndexOffset];
    }

    // Stores the emptiness map cache.
    protected final void setEmptinessMapCache(int chunkX, int chunkZ, boolean[] emptinessMap) {
        this.emptinessMapCache[chunkX + 5 * chunkZ + this.chunkIndexOffset] = emptinessMap;
    }

    // Publishes every changed nibble and marks rerenders.
    protected final void updateVisible() {
        for (int index = 0, max = this.nibbleCache.length; index < max; ++index) {
            SWMRNibbleArray nibble = this.nibbleCache[index];
            boolean notify = this.notifyUpdateCache[index];
            if (!notify && (nibble == null || !nibble.isDirty())) {
                continue;
            }
            if (nibble != null) {
                nibble.updateVisible();
            }
            onNibbleVisible(index, nibble);
            if (notify && this.isClientSide) {
                markRenderUpdate(index, this.notifyBoundsCache[index]);
            }
        }
    }

    // Records a rerender box of a section.
    private void markRenderUpdate(int cacheIndex, long bounds) {
        int localChunkX = cacheIndex % 5;
        int localChunkZ = (cacheIndex / 5) % 5;
        int localSectionY = cacheIndex / 25;
        int sectionX = (localChunkX - this.chunkOffsetX) << 4;
        int sectionY = (localSectionY - this.chunkOffsetY) << 4;
        int sectionZ = (localChunkZ - this.chunkOffsetZ) << 4;
        this.world.markBlockRangeForRenderUpdate(
                sectionX + RenderBounds.minX(bounds), sectionY + RenderBounds.minY(bounds), sectionZ + RenderBounds.minZ(bounds),
                sectionX + RenderBounds.maxX(bounds), sectionY + RenderBounds.maxY(bounds), sectionZ + RenderBounds.maxZ(bounds));
    }

    // Clears every cache.
    protected final void destroyCaches() {
        Arrays.fill(this.sectionCache, null);
        Arrays.fill(this.nibbleCache, null);
        Arrays.fill(this.chunkCache, null);
        Arrays.fill(this.emptinessMapCache, null);
        Arrays.fill(this.fluidCapCache, null);
        if (this.isClientSide) {
            Arrays.fill(this.notifyUpdateCache, false);
        }
    }

    // Returns the block state.
    protected final IBlockState getBlockState(int worldX, int worldY, int worldZ) {
        ExtendedBlockStorage section = this.sectionCache[(worldX >> 4) + 5 * (worldZ >> 4) + (5 * 5) * (worldY >> 4) + this.chunkSectionIndexOffset];
        return section == null ? AIR : section.get(worldX & 15, worldY & 15, worldZ & 15);
    }

    // Returns a block state of a cached section.
    protected final IBlockState getBlockStateFast(int sectionIndex, int x, int y, int z) {
        ExtendedBlockStorage section = this.sectionCache[sectionIndex];
        return section == null ? AIR : section.get(x, y, z);
    }

    // Returns the light level.
    protected int getLightLevel(int worldX, int worldY, int worldZ) {
        SWMRNibbleArray nibble = this.nibbleCache[(worldX >> 4) + 5 * (worldZ >> 4) + (5 * 5) * (worldY >> 4) + this.chunkSectionIndexOffset];
        return nibble == null ? 0 : nibble.getUpdating((worldX & 15) | ((worldZ & 15) << 4) | ((worldY & 15) << 8));
    }

    // Returns the light level of a cached section index.
    protected int getLightLevel(int sectionIndex, int localIndex) {
        SWMRNibbleArray nibble = this.nibbleCache[sectionIndex];
        return nibble == null ? 0 : nibble.getUpdating(localIndex);
    }

    // Stores the light level.
    protected void setLightLevel(int worldX, int worldY, int worldZ, int level) {
        int sectionIndex = (worldX >> 4) + 5 * (worldZ >> 4) + (5 * 5) * (worldY >> 4) + this.chunkSectionIndexOffset;
        SWMRNibbleArray nibble = this.nibbleCache[sectionIndex];
        if (nibble == null) {
            return;
        }
        int localIndex = (worldX & 15) | ((worldZ & 15) << 4) | ((worldY & 15) << 8);
        if (nibble.getUpdating(localIndex) == level) {
            return;
        }
        nibble.set(localIndex, level);
        postLightUpdate(sectionIndex, worldX & 15, worldY & 15, worldZ & 15);
    }

    // Records a light change of a section.
    protected final void postLightUpdate(int sectionIndex, int localX, int localY, int localZ) {
        if (!this.isClientSide) {
            return;
        }
        long point = RenderBounds.pack(localX, localY, localZ, localX, localY, localZ);
        if (!this.notifyUpdateCache[sectionIndex]) {
            this.notifyUpdateCache[sectionIndex] = true;
            this.notifyBoundsCache[sectionIndex] = point;
        } else {
            this.notifyBoundsCache[sectionIndex] = RenderBounds.union(this.notifyBoundsCache[sectionIndex], point);
        }
    }

    // Resets the task state.
    protected abstract void resetTaskState();

    // Returns the emptiness map.
    protected abstract boolean[] getEmptinessMap(Chunk chunk);

    // Returns the light nibbles of a chunk.
    protected abstract SWMRNibbleArray[] getNibblesOnChunk(Chunk chunk);

    // Returns whether use chunk.
    protected abstract boolean canUseChunk(Chunk chunk);

    // Called when a cached nibble is published.
    protected void onNibbleVisible(int cacheIndex, SWMRNibbleArray nibble) {
    }
}
