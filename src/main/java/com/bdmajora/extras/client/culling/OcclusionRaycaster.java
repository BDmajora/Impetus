package com.bdmajora.extras.client.culling;

import net.minecraft.util.math.MathHelper;

// Walks a ray through the opacity grid and stops at the first opaque block.
public final class OcclusionRaycaster {
    // Height of the world in blocks.
    private static final int WORLD_HEIGHT = OcclusionGrid.SECTIONS * 16;

    // Grid the rays walk through.
    private final OcclusionGrid grid;
    // Section of the cached bits on the east axis.
    private int cachedX = Integer.MIN_VALUE;
    // Section of the cached bits on the vertical axis.
    private int cachedY = Integer.MIN_VALUE;
    // Section of the cached bits on the south axis.
    private int cachedZ = Integer.MIN_VALUE;
    // Bits of the cached section or null for an unloaded one.
    private long[] cachedBits;
    // Number of blocks visited since the counter was reset.
    private long steps;

    // Creates a ray walker over the grid.
    public OcclusionRaycaster(OcclusionGrid grid) {
        this.grid = grid;
    }

    // Forgets the cached section after the grid changed.
    public void forgetSection() {
        this.cachedX = Integer.MIN_VALUE;
        this.cachedY = Integer.MIN_VALUE;
        this.cachedZ = Integer.MIN_VALUE;
        this.cachedBits = null;
    }

    // Returns and resets the number of blocks visited.
    public long takeSteps() {
        long taken = this.steps;
        this.steps = 0;
        return taken;
    }

    // Returns the bits of a section through the one entry cache.
    private long[] bits(int sectionX, int sectionY, int sectionZ) {
        if (sectionX != this.cachedX || sectionY != this.cachedY || sectionZ != this.cachedZ) {
            this.cachedBits = this.grid.sectionBits(sectionX, sectionY, sectionZ);
            this.cachedX = sectionX;
            this.cachedY = sectionY;
            this.cachedZ = sectionZ;
        }
        return this.cachedBits;
    }

    // Returns whether no opaque block lies on the ray before the end point less the slack.
    public boolean isClear(double sx, double sy, double sz, double ex, double ey, double ez, double slack) {
        double dx = ex - sx;
        double dy = ey - sy;
        double dz = ez - sz;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(length >= 1.0E-4D)) {
            return true;
        }
        double limit = 1.0D - Math.max(0.0D, slack) / length;
        if (limit <= 0.0D) {
            return true;
        }
        int x = MathHelper.floor(sx);
        int y = MathHelper.floor(sy);
        int z = MathHelper.floor(sz);
        int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
        int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
        int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double deltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dx);
        double deltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dy);
        double deltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0D / dz);
        double maxX = stepX == 0 ? Double.POSITIVE_INFINITY : (stepX > 0 ? x + 1 - sx : sx - x) * deltaX;
        double maxY = stepY == 0 ? Double.POSITIVE_INFINITY : (stepY > 0 ? y + 1 - sy : sy - y) * deltaY;
        double maxZ = stepZ == 0 ? Double.POSITIVE_INFINITY : (stepZ > 0 ? z + 1 - sz : sz - z) * deltaZ;
        // Bounds of the block region known to be empty with min above max for none.
        int emptyMinX = 1;
        int emptyMaxX = 0;
        int emptyMinY = 1;
        int emptyMaxY = 0;
        int emptyMinZ = 1;
        int emptyMaxZ = 0;
        long budget = (long) Math.abs(dx) + (long) Math.abs(dy) + (long) Math.abs(dz) + 4L;
        double t;
        while (budget-- > 0L) {
            if (maxX < maxY) {
                if (maxX < maxZ) {
                    x += stepX;
                    t = maxX;
                    maxX += deltaX;
                } else {
                    z += stepZ;
                    t = maxZ;
                    maxZ += deltaZ;
                }
            } else if (maxY < maxZ) {
                y += stepY;
                t = maxY;
                maxY += deltaY;
            } else {
                z += stepZ;
                t = maxZ;
                maxZ += deltaZ;
            }
            if (t >= limit) {
                return true;
            }
            this.steps++;
            if (x >= emptyMinX && x <= emptyMaxX && y >= emptyMinY && y <= emptyMaxY && z >= emptyMinZ && z <= emptyMaxZ) {
                continue;
            }
            if (y < 0 || y >= WORLD_HEIGHT) {
                if (y < 0 ? stepY <= 0 : stepY >= 0) {
                    return true;
                }
                continue;
            }
            long[] bits = this.bits(x >> 4, y >> 4, z >> 4);
            if (bits == null || bits.length == 0) {
                // Marks the whole section as empty.
                emptyMinX = x & ~15;
                emptyMaxX = emptyMinX + 15;
                emptyMinY = y & ~15;
                emptyMaxY = emptyMinY + 15;
                emptyMinZ = z & ~15;
                emptyMaxZ = emptyMinZ + 15;
                continue;
            }
            long brick = bits[OcclusionGrid.brickIndex(x, y, z)];
            if (brick == 0L) {
                // Marks the four block brick as empty.
                emptyMinX = x & ~3;
                emptyMaxX = emptyMinX + 3;
                emptyMinY = y & ~3;
                emptyMaxY = emptyMinY + 3;
                emptyMinZ = z & ~3;
                emptyMaxZ = emptyMinZ + 3;
                continue;
            }
            if ((brick >>> OcclusionGrid.bitIndex(x, y, z) & 1L) != 0L) {
                return false;
            }
        }
        return true;
    }
}
