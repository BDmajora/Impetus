package com.bdmajora.extras.client.culling;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

// A voxel walk (Amanatides-Woo DDA) from the camera to a point, answering whether any full opaque block lies between; reads chunk sections straight off the client chunk provider with a one-chunk/one-section cache since consecutive rays mostly cross the same sections. Blocks whose state is not a full opaque cube (glass, slabs, leaves, fences) never occlude, which errs toward drawing
public final class OcclusionRaycaster {
    private final WorldClient world;
    private Chunk cachedChunk;
    private int cachedChunkX = Integer.MIN_VALUE;
    private int cachedChunkZ = Integer.MIN_VALUE;
    private ExtendedBlockStorage cachedSection;
    private int cachedSectionY = Integer.MIN_VALUE;

    public OcclusionRaycaster(WorldClient world) {
        this.world = world;
    }

    // True when the segment from (sx,sy,sz) to (ex,ey,ez) is clear; the last `slack` blocks before the end never count, so a target standing inside a block (a mob in a doorway, a chest's own block) is not hidden by itself
    public boolean isClear(double sx, double sy, double sz, double ex, double ey, double ez, double slack) {
        double dx = ex - sx;
        double dy = ey - sy;
        double dz = ez - sz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4D) {
            return true;
        }
        double limit = Math.max(0.0D, length - slack);
        int x = MathHelper.floor(sx);
        int y = MathHelper.floor(sy);
        int z = MathHelper.floor(sz);
        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        // Distance along the ray (in units of the full segment length, 0..1) to the next voxel boundary on each axis, and the distance between boundaries
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(length / dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(length / dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(length / dz);
        double tMaxX = stepX == 0 ? Double.POSITIVE_INFINITY : ((stepX > 0 ? x + 1 - sx : sx - x) * tDeltaX);
        double tMaxY = stepY == 0 ? Double.POSITIVE_INFINITY : ((stepY > 0 ? y + 1 - sy : sy - y) * tDeltaY);
        double tMaxZ = stepZ == 0 ? Double.POSITIVE_INFINITY : ((stepZ > 0 ? z + 1 - sz : sz - z) * tDeltaZ);
        // The camera's own block is skipped: the eye is inside it by definition and it is never an occluder of what the eye sees
        double t = 0.0D;
        int guard = 0;
        while (true) {
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    x += stepX;
                    t = tMaxX;
                    tMaxX += tDeltaX;
                } else {
                    z += stepZ;
                    t = tMaxZ;
                    tMaxZ += tDeltaZ;
                }
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                t = tMaxY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                t = tMaxZ;
                tMaxZ += tDeltaZ;
            }
            if (t >= limit) {
                return true;
            }
            if (this.isOpaque(x, y, z)) {
                return false;
            }
            // Never spin on a degenerate ray
            if (++guard > 512) {
                return true;
            }
        }
    }

    private boolean isOpaque(int x, int y, int z) {
        if (y < 0 || y >= 256) {
            return false;
        }
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        if (chunkX != this.cachedChunkX || chunkZ != this.cachedChunkZ) {
            this.cachedChunk = this.world.getChunkProvider().getLoadedChunk(chunkX, chunkZ);
            this.cachedChunkX = chunkX;
            this.cachedChunkZ = chunkZ;
            this.cachedSectionY = Integer.MIN_VALUE;
        }
        Chunk chunk = this.cachedChunk;
        if (chunk == null) {
            return false;
        }
        int sectionY = y >> 4;
        if (sectionY != this.cachedSectionY) {
            ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
            this.cachedSection = sectionY < sections.length ? sections[sectionY] : null;
            this.cachedSectionY = sectionY;
        }
        ExtendedBlockStorage section = this.cachedSection;
        if (section == null || section == Chunk.NULL_BLOCK_STORAGE) {
            return false;
        }
        IBlockState state = section.get(x & 15, y & 15, z & 15);
        // Air is by far the most common answer and needs no dispatch into the block
        return state.getBlock() != Blocks.AIR && state.isOpaqueCube();
    }
}
