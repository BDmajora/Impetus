package com.bdmajora.fulgor.lighting;

import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

// Square of loaded chunks around a center chunk.
public final class WorldChunkSlice {
    // Width of the square in chunks.
    private static final int DIAMETER = 5;
    // Chunks from the center to an edge.
    private static final int RADIUS = DIAMETER / 2;

    // Chunks of the square by row.
    private final Chunk[] chunks = new Chunk[DIAMETER * DIAMETER];

    // Chunk x of the first column.
    private final int originX;
    // Chunk z of the first row.
    private final int originZ;

    // Creates a world chunk slice from its chunk provider and x and z.
    public WorldChunkSlice(IChunkProvider chunkProvider, int x, int z) {
        for (int xDiff = -RADIUS; xDiff <= RADIUS; xDiff++) {
            for (int zDiff = -RADIUS; zDiff <= RADIUS; zDiff++) {
                this.chunks[((xDiff + RADIUS) * DIAMETER) + (zDiff + RADIUS)] =
                        chunkProvider.getLoadedChunk(x + xDiff, z + zDiff);
            }
        }

        this.originX = x - RADIUS;
        this.originZ = z - RADIUS;
    }

    // Returns the chunk that holds a block position.
    public Chunk getChunkFromWorldCoords(int x, int z) {
        return getChunk((x >> 4) - this.originX, (z >> 4) - this.originZ);
    }

    // Returns whether every chunk within a radius of a block is loaded.
    public boolean isLoaded(int x, int z, int radius) {
        int xStart = ((x - radius) >> 4) - this.originX;
        int zStart = ((z - radius) >> 4) - this.originZ;
        int xEnd = ((x + radius) >> 4) - this.originX;
        int zEnd = ((z + radius) >> 4) - this.originZ;

        for (int currentX = xStart; currentX <= xEnd; currentX++) {
            for (int currentZ = zStart; currentZ <= zEnd; currentZ++) {
                if (getChunk(currentX, currentZ) == null) {
                    return false;
                }
            }
        }

        return true;
    }

    // Returns the chunk at slice coordinates.
    private Chunk getChunk(int x, int z) {
        if (x < 0 || x >= DIAMETER || z < 0 || z >= DIAMETER) {
            return null;
        }

        return this.chunks[(x * DIAMETER) + z];
    }
}
