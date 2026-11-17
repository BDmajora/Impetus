package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

import grondag.bitraster.AbstractRasterizer;
import grondag.bitraster.BoxOccluder;
import grondag.bitraster.Constants;
import grondag.bitraster.Matrix4L;
import grondag.bitraster.PackedBox;
import grondag.bitraster.PerspectiveRasterizer;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import org.joml.Matrix4fc;

// Occlusion buffer that tests and draws sections with a software rasterizer.
public final class RasterOccluder extends BoxOccluder {
    // Squared chunk distance within which sections are drawn but not tested.
    public static final int NEAR_SQUARED_CHUNK_DIST = 3;

    // Number of sections that were tested after a section behind them.
    private int backtrackCount;

    // Work counters that are kept when statistics are on.
    public static long STAT_CENTER_HIT, STAT_AIR_TESTS, STAT_AIR_VISIBLE, STAT_NEAR, STAT_EMPTY_SKIP, STAT_BUDGET_SKIP;
    public static long STAT_TEST_NANOS, STAT_OCCLUDE_NANOS, STAT_SECTIONS, STAT_OCCLUDED_SECTIONS;

    // Result of testing a section.
    public enum SectionVisibility {
        // Nothing is visible and nothing behind is reachable.
        HIDDEN,
        // Nothing is drawn but the space must be traversed.
        TRAVERSABLE,
        // The section is visible and can occlude others.
        VISIBLE
    }

    // Creates an occluder with a perspective rasterizer.
    public RasterOccluder() {
        super(new PerspectiveRasterizer());
    }

    // Returns whether a box is visible from the perspective.
    @Override
    public boolean isBoxVisible(int packedBox, int fuzz) {
        return isBoxVisibleFromPerspective(packedBox, fuzz);
    }

    // Draws a box into the buffer.
    @Override
    public void occludeBox(int packedBox) {
        occludeFromPerspective(packedBox);
    }

    // Clears the buffer for a new search and sizes it for the test distance and rejects a viewport without a matrix.
    public void prepareScene(int viewVersion, Viewport viewport, float testDistance) {
        Matrix4fc vpMatrix = viewport.getVpMatrix();

        if (vpMatrix == null) {
            throw new IllegalStateException("viewport supplies no view matrix");
        }

        var transform = viewport.getTransform();

        this.backtrackCount = 0;

        chooseBufferSize(vpMatrix, testDistance);
        invalidate();
        prepareScene(viewVersion, transform.x, transform.y, transform.z,
                Matrix4L::loadIdentity, m -> copyMatrix(vpMatrix, m));
    }

    // Copies a matrix and transposes it.
    private static void copyMatrix(Matrix4fc src, Matrix4L dst) {
        dst.set(
                src.m00(), src.m10(), src.m20(), src.m30(),
                src.m01(), src.m11(), src.m21(), src.m31(),
                src.m02(), src.m12(), src.m22(), src.m32(),
                src.m03(), src.m13(), src.m23(), src.m33());
    }

    // Sizes the buffer to one pixel per block at the search distance.
    private void chooseBufferSize(Matrix4fc vp, float searchDistance) {
        // Keeps the buffer for matrices without a perspective row
        float perspectiveRow = (float) Math.sqrt((vp.m03() * vp.m03()) + (vp.m13() * vp.m13()) + (vp.m23() * vp.m23()));

        if (!(perspectiveRow > 0.5f) || !(searchDistance > 0)) {
            return;
        }

        float scaleX = (float) Math.sqrt((vp.m00() * vp.m00()) + (vp.m10() * vp.m10()) + (vp.m20() * vp.m20()));
        float scaleY = (float) Math.sqrt((vp.m01() * vp.m01()) + (vp.m11() * vp.m11()) + (vp.m21() * vp.m21()));

        if (!(scaleX > 0) || !(scaleY > 0)) {
            return;
        }

        // Calculates the pixels needed on each axis
        float required = 2.0f * searchDistance * PIXELS_PER_BLOCK;
        int widthTiles = chooseAxisTiles(bufferWidth() >> Constants.TILE_AXIS_SHIFT, required / scaleX);
        int heightTiles = chooseAxisTiles(bufferHeight() >> Constants.TILE_AXIS_SHIFT, required / scaleY);
        resizeBuffer(widthTiles, heightTiles);
    }

    // Returns the tile count of an axis with hysteresis.
    private static int chooseAxisTiles(int currentTiles, float requiredPixels) {
        int required = tilesFor(requiredPixels);

        if (required > currentTiles) {
            return required;
        }

        int shrunk = Math.min(Constants.MAX_SIZE_TILES, required + SIZE_STEP_TILES);

        return shrunk < currentTiles ? shrunk : currentTiles;
    }

    // Returns the tiles that hold a number of pixels within the limits.
    private static int tilesFor(float pixels) {
        int tiles = ((int) Math.ceil(pixels / (8 * SIZE_STEP_TILES))) * SIZE_STEP_TILES;

        return Math.max(Constants.MIN_SIZE_TILES, Math.min(Constants.MAX_SIZE_TILES, tiles));
    }

    // Tiles in one size step.
    private static final int SIZE_STEP_TILES = 8;

    // Pixels a block at the search distance spans.
    private static final float PIXELS_PER_BLOCK = 1.0f;

    // Tests the bounds of a section and then its full space.
    public SectionVisibility testSection(int originX, int originY, int originZ, int squaredChunkDist, int bounds) {
        if (!AbstractRasterizer.STATS) {
            return testSection0(originX, originY, originZ, squaredChunkDist, bounds);
        }

        long t0 = System.nanoTime();

        try {
            STAT_SECTIONS++;
            var result = testSection0(originX, originY, originZ, squaredChunkDist, bounds);

            if (squaredChunkDist <= NEAR_SQUARED_CHUNK_DIST) {
                STAT_NEAR++;
            } else if (bounds == PackedBox.FULL_BOX) {
                STAT_AIR_TESTS++;
                if (result == SectionVisibility.VISIBLE) STAT_AIR_VISIBLE++;
            }

            return result;
        } finally {
            STAT_TEST_NANOS += System.nanoTime() - t0;
        }
    }

    // Draws the occluder boxes of a section that was not tested.
    public void occludeSectionAt(int originX, int originY, int originZ, int[] data) {
        prepareRegion(originX, originY, originZ, PackedBox.rangeFromSquareChunkDist(0), 0);
        occludeSection(data);
    }

    // Draws the occluder boxes of the prepared section.
    public void occludeSection(int[] data) {
        if (!AbstractRasterizer.STATS) {
            occlude(data);
            return;
        }

        long t0 = System.nanoTime();
        STAT_OCCLUDED_SECTIONS++;
        occlude(data);
        STAT_OCCLUDE_NANOS += System.nanoTime() - t0;
    }

    // Tests a section without counting statistics.
    private SectionVisibility testSection0(int originX, int originY, int originZ, int squaredChunkDist, int bounds) {
        prepareRegion(originX, originY, originZ,
                PackedBox.rangeFromSquareChunkDist(squaredChunkDist), squaredChunkDist);

        if (squaredChunkDist <= NEAR_SQUARED_CHUNK_DIST) {
            return SectionVisibility.VISIBLE;
        }

        if (isBoxCenterClear(bounds)) {
            if (AbstractRasterizer.STATS) STAT_CENTER_HIT++;
            return SectionVisibility.VISIBLE;
        }

        // Tests without extra margin
        if (isBoxVisible(bounds, 0)) {
            return SectionVisibility.VISIBLE;
        }

        if (squaredChunkDist < maxSquaredChunkDistance()) {
            this.backtrackCount++;
        }

        // Tests the full space when the bounds were smaller
        if (bounds != PackedBox.FULL_BOX && (isBoxCenterClear(PackedBox.FULL_BOX) || isBoxVisible(PackedBox.FULL_BOX, 0))) {
            return SectionVisibility.TRAVERSABLE;
        }

        return SectionVisibility.HIDDEN;
    }

    // Returns the number of sections that were tested out of order.
    public int backtrackCount() {
        return this.backtrackCount;
    }
}
