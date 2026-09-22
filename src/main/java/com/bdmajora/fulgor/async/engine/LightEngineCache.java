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

// The 5x5 chunk window one task works in (Starlight's StarLightEngine cache): sections, nibbles and emptiness maps indexed x + 5z + 25y relative to the window, recentred per task and cleared before the engine returns to its pool
abstract class LightEngineCache {
    static final int MIN_SECTION = 0;
    static final int MAX_SECTION = 15;
    static final int MIN_LIGHT_SECTION = ChunkLightHelper.MIN_LIGHT_SECTION;
    static final int MAX_LIGHT_SECTION = ChunkLightHelper.MAX_LIGHT_SECTION;
    // One section of slack above and below the light sections so a neighbour index never leaves the array
    private static final int CACHE_SECTIONS = ChunkLightHelper.LIGHT_SECTIONS + 2;
    private static final int CACHE_SIZE = 5 * 5 * CACHE_SECTIONS;
    static final IBlockState AIR = Blocks.AIR.getDefaultState();

    protected final ExtendedBlockStorage[] sectionCache = new ExtendedBlockStorage[CACHE_SIZE];
    protected final SWMRNibbleArray[] nibbleCache = new SWMRNibbleArray[CACHE_SIZE];
    protected final boolean[] notifyUpdateCache = new boolean[CACHE_SIZE];
    protected final long[] notifyBoundsCache = new long[CACHE_SIZE];
    protected final Chunk[] chunkCache = new Chunk[5 * 5];
    protected final boolean[][] emptinessMapCache = new boolean[5 * 5][];
    protected final Object[] fluidCapCache = new Object[5 * 5];
    protected final boolean isClientSide;
    protected final World world;
    private final BlockPos.MutableBlockPos contextualLightPos = new BlockPos.MutableBlockPos();
    protected int encodeOffsetX;
    protected int encodeOffsetY;
    protected int encodeOffsetZ;
    protected int coordinateOffset;
    protected int chunkOffsetX;
    protected int chunkOffsetY;
    protected int chunkOffsetZ;
    protected int chunkIndexOffset;
    protected int chunkSectionIndexOffset;

    LightEngineCache(World world) {
        this.isClientSide = world.isRemote;
        this.world = world;
    }

    // Loads the window around a centre; the outer ring only contributes emptiness maps, and a missing 1-radius neighbour is an error unless relaxed
    protected final void setupCaches(int centerX, int centerY, int centerZ, boolean relaxed, boolean tryToLoadChunksFor2Radius) {
        // Reset first so an overflow stays observable after the task returns and stale queue lengths never cross tasks
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

    protected final Chunk getChunkInCache(int chunkX, int chunkZ) {
        return this.chunkCache[chunkX + 5 * chunkZ + this.chunkIndexOffset];
    }

    protected final void setChunkInCache(int chunkX, int chunkZ, Chunk chunk) {
        this.chunkCache[chunkX + 5 * chunkZ + this.chunkIndexOffset] = chunk;
    }

    // The packed light info for a state at a position, with position-aware blocks and the fluid layer resolved
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

    protected final ExtendedBlockStorage getChunkSection(int chunkX, int chunkY, int chunkZ) {
        return this.sectionCache[chunkX + 5 * chunkZ + (5 * 5) * chunkY + this.chunkSectionIndexOffset];
    }

    protected final void setBlocksForChunkInCache(int chunkX, int chunkZ, ExtendedBlockStorage[] sections) {
        for (int sectionY = MIN_LIGHT_SECTION; sectionY <= MAX_LIGHT_SECTION; ++sectionY) {
            ExtendedBlockStorage section = sections == null || sectionY < MIN_SECTION || sectionY > MAX_SECTION
                    ? null : sections[sectionY];
            this.sectionCache[chunkX + 5 * chunkZ + 5 * 5 * sectionY + this.chunkSectionIndexOffset] = section;
        }
    }

    protected final SWMRNibbleArray getNibbleFromCache(int chunkX, int chunkY, int chunkZ) {
        return this.nibbleCache[chunkX + 5 * chunkZ + (5 * 5) * chunkY + this.chunkSectionIndexOffset];
    }

    protected final void setNibbleInCache(int chunkX, int chunkY, int chunkZ, SWMRNibbleArray nibble) {
        this.nibbleCache[chunkX + 5 * chunkZ + (5 * 5) * chunkY + this.chunkSectionIndexOffset] = nibble;
    }

    protected final void setNibblesForChunkInCache(int chunkX, int chunkZ, SWMRNibbleArray[] nibbles) {
        for (int sectionY = MIN_LIGHT_SECTION; sectionY <= MAX_LIGHT_SECTION; ++sectionY) {
            setNibbleInCache(chunkX, sectionY, chunkZ, nibbles == null ? null : nibbles[sectionY - MIN_LIGHT_SECTION]);
        }
    }

    protected final boolean[] getEmptinessMap(int chunkX, int chunkZ) {
        return this.emptinessMapCache[chunkX + 5 * chunkZ + this.chunkIndexOffset];
    }

    protected final void setEmptinessMapCache(int chunkX, int chunkZ, boolean[] emptinessMap) {
        this.emptinessMapCache[chunkX + 5 * chunkZ + this.chunkIndexOffset] = emptinessMap;
    }

    // Publishes every dirty nibble in the window, mirrors it into vanilla storage, and on the client marks the changed range for a rebuild
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

    protected final IBlockState getBlockState(int worldX, int worldY, int worldZ) {
        ExtendedBlockStorage section = this.sectionCache[(worldX >> 4) + 5 * (worldZ >> 4) + (5 * 5) * (worldY >> 4) + this.chunkSectionIndexOffset];
        return section == null ? AIR : section.get(worldX & 15, worldY & 15, worldZ & 15);
    }

    protected final IBlockState getBlockStateFast(int sectionIndex, int x, int y, int z) {
        ExtendedBlockStorage section = this.sectionCache[sectionIndex];
        return section == null ? AIR : section.get(x, y, z);
    }

    protected int getLightLevel(int worldX, int worldY, int worldZ) {
        SWMRNibbleArray nibble = this.nibbleCache[(worldX >> 4) + 5 * (worldZ >> 4) + (5 * 5) * (worldY >> 4) + this.chunkSectionIndexOffset];
        return nibble == null ? 0 : nibble.getUpdating((worldX & 15) | ((worldZ & 15) << 4) | ((worldY & 15) << 8));
    }

    protected int getLightLevel(int sectionIndex, int localIndex) {
        SWMRNibbleArray nibble = this.nibbleCache[sectionIndex];
        return nibble == null ? 0 : nibble.getUpdating(localIndex);
    }

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

    // Grows the section's changed-block bounds for the render mark; server side has no renderer to tell
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

    // Lane-specific per-task state such as the BFS queue lengths
    protected abstract void resetTaskState();

    protected abstract boolean[] getEmptinessMap(Chunk chunk);

    protected abstract SWMRNibbleArray[] getNibblesOnChunk(Chunk chunk);

    protected abstract boolean canUseChunk(Chunk chunk);

    // Called after a dirty nibble is published
    protected void onNibbleVisible(int cacheIndex, SWMRNibbleArray nibble) {
    }
}
