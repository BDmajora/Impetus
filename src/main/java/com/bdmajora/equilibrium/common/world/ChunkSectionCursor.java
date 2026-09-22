package com.bdmajora.equilibrium.common.world;

import net.minecraft.util.math.BlockPos;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

// Cursor for reading many blocks from one region, holding the last chunk and section and re-resolving at boundaries; short-lived and unshared, and whether unloaded chunks load is the caller's choice since vanilla is inconsistent about it
public final class ChunkSectionCursor {
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();

    private final World world;

    // Null when the world does not implement ChunkAccess, i.e. the option is off.
    private final ChunkAccess access;

    // True in the debug world, where block states are generated on read and not held in sections.
    private final boolean synthetic;

    // Whether a read outside the loaded area should load the chunk, as getBlockState does.
    private final boolean loadChunks;

    private Chunk chunk;
    private int chunkX = Integer.MIN_VALUE;
    private int chunkZ = Integer.MIN_VALUE;

    private ExtendedBlockStorage section;
    private int sectionY = Integer.MIN_VALUE;

    // loadChunks true matches World#getBlockState, false treats unloaded chunks as air like World#getCollisionBoxes
    public ChunkSectionCursor(World world, boolean loadChunks) {
        this.world = world;
        this.access = world instanceof ChunkAccess ? (ChunkAccess) world : null;
        this.synthetic = world.getWorldType() == WorldType.DEBUG_ALL_BLOCK_STATES;
        this.loadChunks = loadChunks;
    }

    // The state at this position, or air if it is outside the world or in a chunk we cannot see.
    public IBlockState getBlockState(int x, int y, int z) {
        if (y < 0 || y > 255) {
            return AIR;
        }

        if (this.synthetic || this.access == null) {
            return this.world.getBlockState(new BlockPos(x, y, z));
        }

        int newChunkX = x >> 4;
        int newChunkZ = z >> 4;
        int newSectionY = y >> 4;

        if (newChunkX != this.chunkX || newChunkZ != this.chunkZ) {
            this.chunkX = newChunkX;
            this.chunkZ = newChunkZ;
            this.chunk = this.loadChunks
                    ? this.access.equilibrium$getChunkCached(newChunkX, newChunkZ)
                    : this.access.equilibrium$getLoadedChunk(newChunkX, newChunkZ);

            // Force the section to be re-resolved; the y index alone no longer identifies it.
            this.sectionY = Integer.MIN_VALUE;
        }

        if (this.chunk == null) {
            return AIR;
        }

        if (newSectionY != this.sectionY) {
            this.sectionY = newSectionY;

            ExtendedBlockStorage[] sections = this.chunk.getBlockStorageArray();
            ExtendedBlockStorage candidate = newSectionY < sections.length ? sections[newSectionY] : null;

            this.section = candidate == Chunk.NULL_BLOCK_STORAGE ? null : candidate;
        }

        return this.section == null ? AIR : this.section.get(x & 15, y & 15, z & 15);
    }
}
