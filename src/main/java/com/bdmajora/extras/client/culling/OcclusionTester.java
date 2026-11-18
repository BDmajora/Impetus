package com.bdmajora.extras.client.culling;

import net.minecraft.util.math.MathHelper;

// Tests whether any part of a box can be seen from the camera point.
public final class OcclusionTester {
    // Largest number of gaps between the sample points along one side of a face.
    static final int MAX_SPANS = 6;
    // Starting number of slots of the point cache.
    private static final int CACHE_CAPACITY = 1 << 15;

    // Ray walker of the grid.
    private final OcclusionRaycaster raycaster;
    // Answers of the lattice points tested this pass.
    private final VisibilityCache cache = new VisibilityCache(CACHE_CAPACITY);
    // East position of the camera.
    private double camX;
    // Height of the camera.
    private double camY;
    // South position of the camera.
    private double camZ;
    // Ray length at the end of each ray that never occludes.
    private double slack;
    // Squared distance beyond which every box counts as visible.
    private double maxDistanceSq;
    // Number of rays cast this pass.
    private int rays;

    // Creates a tester over the grid.
    public OcclusionTester(OcclusionGrid grid) {
        this.raycaster = new OcclusionRaycaster(grid);
    }

    // Starts a pass from a camera point.
    public void begin(double camX, double camY, double camZ, double slack, double maxDistance) {
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
        this.slack = slack;
        this.maxDistanceSq = maxDistance * maxDistance;
        this.rays = 0;
        this.cache.clear();
        this.raycaster.forgetSection();
    }

    // Returns the number of rays cast this pass.
    public int rays() {
        return this.rays;
    }

    // Returns the ray walker.
    OcclusionRaycaster raycaster() {
        return this.raycaster;
    }

    // Returns the point cache.
    VisibilityCache cache() {
        return this.cache;
    }

    // Returns whether any part of the box can be seen.
    public boolean isVisible(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double gapX = Math.max(0.0D, Math.max(minX - this.camX, this.camX - maxX));
        double gapY = Math.max(0.0D, Math.max(minY - this.camY, this.camY - maxY));
        double gapZ = Math.max(0.0D, Math.max(minZ - this.camZ, this.camZ - maxZ));
        if (gapX * gapX + gapY * gapY + gapZ * gapZ > this.maxDistanceSq) {
            return true;
        }
        int startX = MathHelper.floor(minX);
        int startY = MathHelper.floor(minY);
        int startZ = MathHelper.floor(minZ);
        int endX = Math.max(MathHelper.ceil(maxX), startX + 1);
        int endY = Math.max(MathHelper.ceil(maxY), startY + 1);
        int endZ = Math.max(MathHelper.ceil(maxZ), startZ + 1);
        // A camera within a block of the lattice hull always sees the box.
        if (this.camX >= startX - 1 && this.camX <= endX + 1 && this.camY >= startY - 1 && this.camY <= endY + 1
                && this.camZ >= startZ - 1 && this.camZ <= endZ + 1) {
            return true;
        }
        this.rays++;
        if (this.raycaster.isClear(this.camX, this.camY, this.camZ, (minX + maxX) * 0.5D, (minY + maxY) * 0.5D, (minZ + maxZ) * 0.5D, this.slack)) {
            return true;
        }
        if (this.camX < startX) {
            if (this.faceVisible(startX, startX, startY, endY, startZ, endZ)) {
                return true;
            }
        } else if (this.camX > endX && this.faceVisible(endX, endX, startY, endY, startZ, endZ)) {
            return true;
        }
        if (this.camY < startY) {
            if (this.faceVisible(startX, endX, startY, startY, startZ, endZ)) {
                return true;
            }
        } else if (this.camY > endY && this.faceVisible(startX, endX, endY, endY, startZ, endZ)) {
            return true;
        }
        if (this.camZ < startZ) {
            return this.faceVisible(startX, endX, startY, endY, startZ, startZ);
        }
        return this.camZ > endZ && this.faceVisible(startX, endX, startY, endY, endZ, endZ);
    }

    // Returns the spacing of the samples along a side.
    static int stride(int from, int to) {
        int span = to - from;
        return span <= MAX_SPANS ? 1 : (span + MAX_SPANS - 1) / MAX_SPANS;
    }

    // Returns whether any sampled lattice point of the face is visible.
    private boolean faceVisible(int x0, int x1, int y0, int y1, int z0, int z1) {
        int strideX = stride(x0, x1);
        int strideY = stride(y0, y1);
        int strideZ = stride(z0, z1);
        for (int x = x0; ; x = Math.min(x + strideX, x1)) {
            for (int y = y0; ; y = Math.min(y + strideY, y1)) {
                for (int z = z0; ; z = Math.min(z + strideZ, z1)) {
                    if (this.pointVisible(x, y, z)) {
                        return true;
                    }
                    if (z == z1) {
                        break;
                    }
                }
                if (y == y1) {
                    break;
                }
            }
            if (x == x1) {
                return false;
            }
        }
    }

    // Returns whether a lattice point is visible and casts its ray once per pass.
    boolean pointVisible(int x, int y, int z) {
        int known = this.cache.get(x, y, z);
        if (known != VisibilityCache.UNKNOWN) {
            return known == VisibilityCache.VISIBLE;
        }
        this.rays++;
        boolean visible = this.raycaster.isClear(this.camX, this.camY, this.camZ, x, y, z, this.slack);
        this.cache.put(x, y, z, visible);
        return visible;
    }
}
