package com.bdmajora.impetus.engine.impl.render.chunk.multidraw;

import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests the validity interval at the int limits.
class BatchAssemblerLimitsTest {
    // Returns the lower end of an interval.
    private static int low(long interval) {
        return (int) interval;
    }

    // Returns the upper end of an interval.
    private static int high(long interval) {
        return (int) (interval >>> 32);
    }

    // Keeps the camera inside its interval for ordinary and extreme positions.
    @Test
    void intervalContainsCamera() {
        int[] cameras = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -100000, -33, -2, -1, 0, 1, 15, 16, 17, 18, 19, 20, 100000, Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
        int[] regionStarts = {Integer.MIN_VALUE / 16, -9, -1, 0, 8, Integer.MAX_VALUE / 16 - 8};
        for (int camera : cameras) {
            for (int start : regionStarts) {
                long interval = BatchAssembler.cameraValidityInterval(camera, start, 8);
                assertTrue(low(interval) <= camera, camera + " " + start);
                assertTrue(camera < high(interval) || high(interval) == Integer.MAX_VALUE, camera + " " + start);
            }
        }
    }

    // Opens the interval to the limits for a camera far from the region.
    @Test
    void farCameraHasWideInterval() {
        long interval = BatchAssembler.cameraValidityInterval(1_000_000, 0, 8);
        assertTrue(high(interval) == Integer.MAX_VALUE);
        assertTrue(low(interval) > 0);
        long other = BatchAssembler.cameraValidityInterval(-1_000_000, 0, 8);
        assertEquals(Integer.MIN_VALUE, low(other));
        assertTrue(high(other) < 0 || high(other) <= 0);
    }

    // Keeps the culling mask of every camera inside its interval equal.
    @Test
    void maskIsConstantInsideTheInterval() {
        for (int camera = -50; camera <= 200; camera += 7) {
            long interval = BatchAssembler.cameraValidityInterval(camera, 0, 4);
            int expected = mask(camera, 0);
            for (int c = Math.max(low(interval), camera - 40); c < Math.min(high(interval), camera + 40); c++) {
                assertEquals(expected, mask(c, 0), "camera " + camera + " other " + c);
            }
        }
    }

    // Returns the combined mask over the sections of one axis of a four section region.
    private static int mask(int camera, int minChunk) {
        int combined = 0;
        for (int chunk = minChunk; chunk < minChunk + 4; chunk++) {
            combined = combined * 131 + BatchAssembler.getVisibleFaces(camera, 0, 0, chunk, 0, 0);
        }
        return combined;
    }

    // Always marks the unassigned facing visible.
    @Test
    void unassignedAlwaysVisible() {
        int bit = 1 << ModelQuadFacing.UNASSIGNED.ordinal();
        for (int camera : new int[]{Integer.MIN_VALUE, -1, 0, Integer.MAX_VALUE}) {
            assertNotEquals(0, BatchAssembler.getVisibleFaces(camera, camera, camera, 0, 0, 0) & bit);
        }
    }
}
