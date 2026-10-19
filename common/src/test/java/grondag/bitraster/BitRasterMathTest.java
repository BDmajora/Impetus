package grondag.bitraster;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class BitRasterMathTest {
    @Test
    void fixedPointMatricesAgreeWithIntegerDotProducts() throws Exception {
        Matrix4L matrix = new Matrix4L();
        matrix.set(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
        long unit = Matrix4L.MATRIX_PRECISION_UNITY;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                long expected = (row * 4 + col + 1) * unit;
                assertEquals(expected, Matrix4L.class.getMethod("a" + row + col).invoke(matrix));
                assertEquals((float) (row * 4 + col + 1), Matrix4L.class.getMethod("a" + row + col + "f").invoke(matrix));
            }
        }
        assertEquals(18 * unit, matrix.transformVec4X(1, 2, 3));
        assertEquals(46 * unit, matrix.transformVec4Y(1, 2, 3));
        assertEquals(74 * unit, matrix.transformVec4Z(1, 2, 3));
        assertEquals(102 * unit, matrix.transformVec4W(1, 2, 3));
        assertEquals(22 * unit, matrix.transformVec4X(1, 2, 3, 2));
        assertEquals(54 * unit, matrix.transformVec4Y(1, 2, 3, 2));
        assertEquals(86 * unit, matrix.transformVec4Z(1, 2, 3, 2));
        assertEquals(118 * unit, matrix.transformVec4W(1, 2, 3, 2));
        Matrix4L copy = new Matrix4L();
        copy.copyFrom(matrix);
        assertTrue(copy.matches(matrix));
        copy.loadIdentity();
        assertFalse(copy.matches(matrix));
        matrix.multiply(copy);
        assertTrue(matrix.toString().startsWith("[65536, 131072"));
    }

    @Test
    void repeatedTranslationPreservesHomogeneousCoordinatesAndExistingOffsets() {
        Matrix4L matrix = new Matrix4L();
        matrix.loadIdentity();
        matrix.translate(2, 3, 4, 0);
        matrix.translate(1, -2, 3, 0);
        assertEquals(3 * Matrix4L.MATRIX_PRECISION_UNITY, matrix.transformVec4X(0, 0, 0));
        assertEquals(Matrix4L.MATRIX_PRECISION_UNITY, matrix.transformVec4Y(0, 0, 0));
        assertEquals(7 * Matrix4L.MATRIX_PRECISION_UNITY, matrix.transformVec4Z(0, 0, 0));
        assertEquals(Matrix4L.MATRIX_PRECISION_UNITY, matrix.transformVec4W(0, 0, 0));
    }

    @Test
    void everyTilePixelHasOneDistinctBitAndMortonIndex() {
        var morton = new HashSet<Integer>();
        long filled = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                assertEquals(y * 8 + x, Indexer.pixelIndex(x, y));
                assertTrue(morton.add(Indexer.mortonNumber(x, y)));
                assertTrue(Indexer.isPixelClear(filled, x, y));
                assertTrue(Indexer.testPixelInWordPreMasked(filled, x, y));
                filled = Indexer.setPixelInWordPreMasked(filled, x, y);
                assertFalse(Indexer.isPixelClear(filled, x, y));
                assertEquals(1L << (y * 8 + x), Indexer.pixelMask(x, y));
            }
        }
        assertEquals(-1L, filled);
        assertNotNull(new Constants());
        assertEquals(63, Constants.UP | Constants.DOWN | Constants.EAST | Constants.WEST | Constants.NORTH | Constants.SOUTH);
        assertEquals("(1, 2, 3), (4, 5, 6)", PackedBox.toString(PackedBox.pack(1, 2, 3, 4, 5, 6, 0)));
    }

    @Test
    void diagnosticsPrintMasksAndResetTimerSamples() {
        PrintStream previous = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(output)) {
            System.setOut(capture);
            assertNotNull(new Util());
            Util.printMask(1L);
            Util.printSpaced("10");
            MicroTimer timer = new MicroTimer("raster", 2);
            assertEquals(0, timer.hits());
            timer.reportAndClear();
            timer.start();
            assertFalse(timer.stop(false));
            assertEquals(1, timer.hits());
            assertTrue(timer.elapsed() >= 0);
            timer.start();
            assertTrue(timer.stop(true));
            assertEquals(0, timer.hits());
            assertEquals(0, timer.elapsed());
        } finally {
            System.setOut(previous);
        }
        assertTrue(output.toString().contains("X - "));
        assertTrue(output.toString().contains("Avg raster duration"));
        assertTrue(output.toString().contains("Subset avg duration"));
    }
}
