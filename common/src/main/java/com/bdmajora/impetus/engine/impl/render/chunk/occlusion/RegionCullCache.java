package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

import com.bdmajora.impetus.engine.impl.render.viewport.CameraTransform;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;

import java.util.Arrays;

import static com.bdmajora.impetus.engine.impl.common.util.MathUtil.nearestToZero;
import static com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion.*;

// Memoizes the visibility class of each region during one search.
final class RegionCullCache {
    // Every section passes both tests.
    static final int FULLY_INSIDE = Frustum.FULLY_INSIDE;
    // Both tests are inconclusive.
    static final int PARTIAL = Frustum.PARTIALLY_INSIDE;
    // Every section passes the distance test.
    static final int PARTIAL_DISTANCE_IN = 2;
    // Every section passes the frustum test.
    static final int PARTIAL_FRUSTUM_IN = 3;
    // Every section fails a test.
    static final int OUTSIDE = 4;

    // Value of a region that was not classified.
    static final byte UNCOMPUTED = -1;

    // Padding of the frustum box in blocks.
    private static final float FRUSTUM_PAD = 1.125f + 0.03125f;

    // Slack of the distance bounds in blocks.
    private static final float DISTANCE_EPSILON = 0.001f;

    // Starting size of the cache.
    private static final int INITIAL_CAPACITY = 64;

    // Class of each region by id.
    private byte[] classification = new byte[INITIAL_CAPACITY];

    // Viewport of the search.
    private Viewport viewport;
    // Camera of the search.
    private CameraTransform camera;
    // Search distance.
    private float maxDistance;

    // Starts a search and clears the classes and rejects a null viewport or a negative region count.
    void begin(Viewport viewport, float searchDistance, int numRegions) {
        java.util.Objects.requireNonNull(viewport, "viewport");
        if (numRegions < 0) {
            throw new IllegalArgumentException("Negative region count " + numRegions);
        }
        this.viewport = viewport;
        this.camera = viewport.getTransform();
        this.maxDistance = searchDistance;
        if (numRegions > this.classification.length) {
            this.grow(numRegions);
        }
        Arrays.fill(this.classification, 0, numRegions, UNCOMPUTED);
    }

    // Returns the class of a region and computes it on first use.
    int classify(int regionId, int regionOriginX, int regionOriginY, int regionOriginZ) {

        byte result = this.classification[regionId];

        if (result == UNCOMPUTED) {
            result = (byte) this.compute(regionOriginX, regionOriginY, regionOriginZ);
            this.classification[regionId] = result;
        }

        return result;
    }

    // Returns the class of a region or the uncomputed marker.
    int cached(int regionId) {
        return this.classification[regionId];
    }

    // Replaces the cache with a larger one.
    private void grow(int minimumCapacity) {
        int capacity = Math.max(minimumCapacity, this.classification.length * 2);
        this.classification = new byte[capacity];
    }

    // Computes the conservative class of a region.
    private int compute(int regionOriginX, int regionOriginY, int regionOriginZ) {
        final CameraTransform t = this.camera;
        final float maxDistance = this.maxDistance;

        final int ax = regionOriginX - t.intX, bx = ax + REGION_BLOCK_WIDTH;
        final int ay = regionOriginY - t.intY, by = ay + REGION_BLOCK_HEIGHT;
        final int az = regionOriginZ - t.intZ, bz = az + REGION_BLOCK_LENGTH;

        // Calculates the nearest distance range on each axis
        final float loX = nearestToZero(ax, ax + 16) - t.fracX;
        final float hiX = nearestToZero(bx - 16, bx) - t.fracX;
        final float loY = nearestToZero(ay, ay + 16) - t.fracY;
        final float hiY = nearestToZero(by - 16, by) - t.fracY;
        final float loZ = nearestToZero(az, az + 16) - t.fracZ;
        final float hiZ = nearestToZero(bz - 16, bz) - t.fracZ;

        // Calculates the largest distance on each axis
        final float farX = maxAbs(loX, hiX) + DISTANCE_EPSILON;
        final float farY = maxAbs(loY, hiY) + DISTANCE_EPSILON;
        final float farZ = maxAbs(loZ, hiZ) + DISTANCE_EPSILON;

        // Calculates the smallest distance on each axis
        final float nearX = Math.max(0.0f, minAbs(loX, hiX) - DISTANCE_EPSILON);
        final float nearY = Math.max(0.0f, minAbs(loY, hiY) - DISTANCE_EPSILON);
        final float nearZ = Math.max(0.0f, minAbs(loZ, hiZ) - DISTANCE_EPSILON);

        final float maxDistanceSq = maxDistance * maxDistance;

        // Rejects the region when the nearest point is too far
        boolean distanceOut = ((nearX * nearX) + (nearZ * nearZ)) >= maxDistanceSq || nearY >= maxDistance;

        if (distanceOut) {
            return OUTSIDE;
        }

        // Accepts the distance only when the farthest point is inside
        boolean distanceIn = ((farX * farX) + (farZ * farZ)) < maxDistanceSq && farY < maxDistance;

        int result = this.viewport.intersectCameraRelativeBox(
                (ax - t.fracX) - FRUSTUM_PAD,
                (ay - t.fracY) - FRUSTUM_PAD,
                (az - t.fracZ) - FRUSTUM_PAD,
                (bx - t.fracX) + FRUSTUM_PAD,
                (by - t.fracY) + FRUSTUM_PAD,
                (bz - t.fracZ) + FRUSTUM_PAD);

        if (result == Frustum.OUTSIDE) {
            return OUTSIDE;
        }

        if (result == Frustum.FULLY_INSIDE) {
            return distanceIn ? FULLY_INSIDE : PARTIAL_FRUSTUM_IN;
        }

        return distanceIn ? PARTIAL_DISTANCE_IN : PARTIAL;
    }

    // Returns the largest absolute value of an interval.
    private static float maxAbs(float lo, float hi) {
        return Math.max(Math.abs(lo), Math.abs(hi));
    }

    // Returns the smallest absolute value of an interval.
    private static float minAbs(float lo, float hi) {
        if (lo > 0.0f) {
            return lo;
        }

        if (hi < 0.0f) {
            return -hi;
        }

        return 0.0f;
    }
}
