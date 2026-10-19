package com.bdmajora.impetus.engine.impl.render.chunk.occlusion.geometry;

import grondag.bitraster.PackedBox;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class GeometryCoverageTest {
    private static boolean bit(long[] data, int index) {
        return (data[index >>> 6] & (1L << (index & 63))) != 0;
    }

    private static void set(long[] data, int index) {
        data[index >>> 6] |= 1L << (index & 63);
    }

    @Test
    void everyRectangleMaskMatchesItsCoordinatesAndSampleOperations() {
        assertNotNull(new Area());
        for (int index = 0; index < Area.AREA_COUNT; index++) {
            int key = Area.indexToKey(index);
            assertEquals(index, Area.keyToIndex(key));
            long[] sample = new long[8];
            for (int y = Area.y0(key); y <= Area.y1(key); y++) {
                for (int x = Area.x0(key); x <= Area.x1(key); x++) set(sample, 256 + x + y * 16);
            }
            assertTrue(Area.isIncludedBySample(sample, 4, index));
            assertTrue(Area.intersectsWithSample(sample, 4, index));
            assertFalse(Area.isAdditive(sample, 4, index));
            for (int word = 0; word < 4; word++) assertEquals(sample[4 + word], Area.bitsFromIndex(index, word));
            Area.clearBits(sample, 4, index);
            assertArrayEquals(new long[8], sample);
            assertFalse(Area.intersectsWithSample(sample, 4, index));
            assertTrue(Area.isAdditive(sample, 4, index));
        }
    }

    private static int largestArea(long[] bits) {
        int[][] sums = new int[17][17];
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            sums[y + 1][x + 1] = sums[y][x + 1] + sums[y + 1][x] - sums[y][x] + (bit(bits, x + y * 16) ? 1 : 0);
        }
        int best = 0;
        for (int y0 = 0; y0 < 16; y0++) for (int y1 = y0 + 1; y1 <= 16; y1++) {
            for (int x0 = 0; x0 < 16; x0++) for (int x1 = x0 + 1; x1 <= 16; x1++) {
                int area = (x1 - x0) * (y1 - y0);
                int count = sums[y1][x1] - sums[y0][x1] - sums[y1][x0] + sums[y0][x0];
                if (count == area) best = Math.max(best, area);
            }
        }
        return best;
    }

    @Test
    void largestRectanglesMatchAnIndependentExhaustiveSearchAndDecompositionPreservesPixels() {
        Random random = new Random(0xB17);
        AreaFinder finder = new AreaFinder();
        for (int trial = 0; trial < 48; trial++) {
            long[] sample = new long[4];
            for (int i = 0; i < 256; i++) if (random.nextInt(5) < 3) set(sample, i);
            int key = AreaFinder.findLargest(sample);
            assertEquals(largestArea(sample), Area.size(key));
            assertTrue(Area.isIncludedBySample(sample, 0, Area.keyToIndex(key)));
            long[] original = sample.clone();
            long[] combined = new long[4];
            finder.find(sample, 0, index -> {
                long[] mask = {Area.bitsFromIndex(index, 0), Area.bitsFromIndex(index, 1), Area.bitsFromIndex(index, 2), Area.bitsFromIndex(index, 3)};
                for (int w = 0; w < 4; w++) {
                    assertEquals(0, combined[w] & mask[w]);
                    combined[w] |= mask[w];
                }
            });
            assertArrayEquals(original, sample);
            assertArrayEquals(original, combined);
        }
        var empty = new ArrayList<Integer>();
        finder.find(new long[4], 0, empty::add);
        assertTrue(empty.isEmpty());
    }

    @Test
    void separateVoxelIslandsAreSortedAndCoveredWithoutOverlaps() {
        long[] source = new long[64];
        for (int z = 1; z < 3; z++) for (int y = 1; y < 3; y++) for (int x = 1; x < 3; x++) set(source, x + y * 16 + z * 256);
        for (int z = 8; z < 12; z++) for (int y = 8; y < 12; y++) for (int x = 8; x < 12; x++) set(source, x + y * 16 + z * 256);
        BoxFinder finder = new BoxFinder(new AreaFinder());
        finder.findBoxes(source, 0);
        assertTrue(finder.boxes.size() >= 2);
        long[] covered = new long[64];
        for (int box : finder.boxes) {
            for (int z = PackedBox.z0(box); z < PackedBox.z1(box); z++) for (int y = PackedBox.y0(box); y < PackedBox.y1(box); y++) for (int x = PackedBox.x0(box); x < PackedBox.x1(box); x++) {
                int index = x + y * 16 + z * 256;
                assertFalse(bit(covered, index));
                set(covered, index);
            }
        }
        assertArrayEquals(source, covered);
    }

    @Test
    void opaqueVoxelBoxesNeverIncludeAirAndUseAllDistanceRanges() {
        BoxFinder finder = new BoxFinder(new AreaFinder());
        int[][] sizes = {{2, 3, 4}, {5, 5, 6}, {10, 10, 10}};
        for (int[] size : sizes) {
            long[] source = new long[64];
            for (int z = 1; z < 1 + size[2]; z++) for (int y = 1; y < 1 + size[1]; y++) for (int x = 1; x < 1 + size[0]; x++) {
                set(source, x + y * 16 + z * 256);
            }
            long[] before = source.clone();
            finder.findBoxes(source, 0);
            assertArrayEquals(before, source);
            assertFalse(finder.boxes.isEmpty());
            for (int box : finder.boxes) {
                for (int z = PackedBox.z0(box); z < PackedBox.z1(box); z++) for (int y = PackedBox.y0(box); y < PackedBox.y1(box); y++) for (int x = PackedBox.x0(box); x < PackedBox.x1(box); x++) {
                    assertTrue(bit(source, x + y * 16 + z * 256), PackedBox.toString(box));
                }
            }
            int volume = Arrays.stream(size).reduce(1, (a, b) -> a * b);
            assertEquals(volume <= 64 ? PackedBox.RANGE_NEAR : volume > 512 ? PackedBox.RANGE_FAR : PackedBox.RANGE_MID,
                    PackedBox.range(finder.boxes.getInt(0)));
        }
    }
}
