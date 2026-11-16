package com.bdmajora.equilibrium.common.world;

import net.minecraft.util.math.BlockPos;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

// Reads block states while it remembers the last chunk and section.
public final class ChunkSectionCursor {
    // Default state of air.
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();

    // World to read.
    private final World world;

    // Chunk lookup of the world or null.
    private final ChunkAccess access;

    // Whether the world generates states on demand.
    private final boolean synthetic;

    // Whether missing chunks are loaded.
    private final boolean loadChunks;

    // Chunk of the last read.
    private Chunk chunk;
    // East position of the chunk.
    private int chunkX = Integer.MIN_VALUE;
    // South position of the chunk.
    private int chunkZ = Integer.MIN_VALUE;

    // Section of the last read.
    private ExtendedBlockStorage section;
    // Height of the section.
    private int sectionY = Integer.MIN_VALUE;

    // Creates a cursor for the world.
    public ChunkSectionCursor(World world, boolean loadChunks) {
        this.world = world;
        this.access = world instanceof ChunkAccess ? (ChunkAccess) world : null;
        this.synthetic = world.getWorldType() == WorldType.DEBUG_ALL_BLOCK_STATES;
        this.loadChunks = loadChunks;
    }

    // Returns the state at the position and air outside the world.
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
