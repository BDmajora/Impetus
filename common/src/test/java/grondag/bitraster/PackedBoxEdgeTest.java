package grondag.bitraster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Tests packing limits and the timer statistics.
class PackedBoxEdgeTest {
    // Round trips every field at its smallest and largest values.
    @Test
    void roundTrip() {
        int box = PackedBox.pack(0, 1, 2, 16, 31, 5, PackedBox.RANGE_EXTREME);
        assertEquals(0, PackedBox.x0(box));
        assertEquals(1, PackedBox.y0(box));
        assertEquals(2, PackedBox.z0(box));
        assertEquals(16, PackedBox.x1(box));
        assertEquals(31, PackedBox.y1(box));
        assertEquals(5, PackedBox.z1(box));
        assertEquals(PackedBox.RANGE_EXTREME, PackedBox.range(box));
        int max = PackedBox.pack(31, 31, 31, 31, 31, 31, 3);
        assertEquals(31, PackedBox.z1(max));
        assertEquals(3, PackedBox.range(max));
        assertEquals("(0, 1, 2), (16, 31, 5)", PackedBox.toString(box));
        assertEquals(PackedBox.EMPTY_BOX, PackedBox.pack(0, 0, 0, 0, 0, 0, 0));
        assertEquals(16, PackedBox.x1(PackedBox.FULL_BOX));
    }

    // Rejects values that would spill into other fields.
    @Test
    void rejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> PackedBox.pack(-1, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> PackedBox.pack(32, 0, 0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> PackedBox.pack(0, 0, 0, 0, 0, Integer.MIN_VALUE, 0));
        assertThrows(IllegalArgumentException.class, () -> PackedBox.pack(0, 0, 0, 0, 0, 0, 4));
        assertThrows(IllegalArgumentException.class, () -> PackedBox.pack(0, 0, 0, 0, 0, 0, -1));
    }

    // Classifies distances at the boundaries.
    @Test
    void ranges() {
        assertEquals(PackedBox.RANGE_NEAR, PackedBox.rangeFromSquareChunkDist(Integer.MIN_VALUE));
        assertEquals(PackedBox.RANGE_NEAR, PackedBox.rangeFromSquareChunkDist(PackedBox.SQUARE_CHUNK_DIST_NEAR));
        assertEquals(PackedBox.RANGE_MID, PackedBox.rangeFromSquareChunkDist(PackedBox.SQUARE_CHUNK_DIST_NEAR + 1));
        assertEquals(PackedBox.RANGE_MID, PackedBox.rangeFromSquareChunkDist(PackedBox.SQUARE_CHUNK_DIST_MID));
        assertEquals(PackedBox.RANGE_FAR, PackedBox.rangeFromSquareChunkDist(PackedBox.SQUARE_CHUNK_DIST_MID + 1));
        assertEquals(PackedBox.RANGE_FAR, PackedBox.rangeFromSquareChunkDist(PackedBox.SQUARE_CHUNK_DIST_FAR));
        assertEquals(PackedBox.RANGE_EXTREME, PackedBox.rangeFromSquareChunkDist(PackedBox.SQUARE_CHUNK_DIST_FAR + 1));
        assertEquals(PackedBox.RANGE_EXTREME, PackedBox.rangeFromSquareChunkDist(Integer.MAX_VALUE));
    }

    // Reports a minimum below the first run and reports at the sample size.
    @Test
    void microTimer() throws InterruptedException {
        assertThrows(NullPointerException.class, () -> new MicroTimer(null, 1));
        MicroTimer timer = new MicroTimer("t", 2);
        timer.start();
        assertFalse(timer.stop(false));
        assertEquals(1, timer.hits());
        assertTrue(timer.elapsed() >= 0);
        timer.start();
        assertTrue(timer.stop(true));
        assertEquals(0, timer.hits());
        timer.reportAndClear();
        MicroTimer never = new MicroTimer("never", 0);
        never.start();
        assertFalse(never.stop(true));
        never.reportAndClear();
    }

    // Packs unit coordinates into pixel indexes and masks.
    @Test
    void indexerRoundTrip() {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                long mask = Indexer.pixelMask(x, y);
                assertEquals(1L, Long.bitCount(mask));
                assertTrue(Indexer.isPixelClear(0L, x, y));
                assertFalse(Indexer.isPixelClear(mask, x, y));
                assertFalse(Indexer.testPixelInWordPreMasked(Indexer.setPixelInWordPreMasked(0L, x, y), x, y));
                assertEquals(Indexer.pixelIndex(x, y), Long.numberOfTrailingZeros(mask));
                assertTrue(Indexer.mortonNumber(x, y) < 64);
            }
        }
        assertTrue(Indexer.isPixelClear(0L, 8, 8));
        assertTrue(Indexer.isPixelClear(0L, -1, -1));
    }
}
