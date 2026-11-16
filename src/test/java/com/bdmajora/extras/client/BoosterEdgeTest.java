package com.bdmajora.extras.client;

import com.bdmajora.extras.client.booster.FastMath;
import com.bdmajora.extras.client.booster.FastRandom;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoosterEdgeTest {
    @Test
    void wrappedAnglesStayInRangeAndMatchTheModulus() {
        float[] floats = {0f, 179.99999f, 180f, -180f, -180.00001f, 540f, -540f, 1e9f, -1e9f, 3e12f, -3e12f, Float.MAX_VALUE, -Float.MAX_VALUE};
        for (float v : floats) {
            float wrapped = FastMath.wrapDegrees(v);
            assertTrue(wrapped >= -180f && wrapped < 180f, v + " -> " + wrapped);
        }
        double[] doubles = {0, 179.99999999999997, 180, -180, 360, 1e15, -1e15, 4e19, -4e19, Double.MAX_VALUE};
        for (double v : doubles) {
            double wrapped = FastMath.wrapDegrees(v);
            assertTrue(wrapped >= -180 && wrapped < 180, v + " -> " + wrapped);
        }
        assertEquals(-170.0, FastMath.wrapDegrees(190.0), 1e-9);
        assertEquals(170f, FastMath.wrapDegrees(-190f), 1e-4f);
        assertTrue(Float.isNaN(FastMath.wrapDegrees(Float.NaN)));
        assertTrue(Double.isNaN(FastMath.wrapDegrees(Double.POSITIVE_INFINITY)));
    }

    @Test
    void logarithmsHandleNonPositiveAndExtremeValues() {
        assertEquals(0, FastMath.ceilLog2(0));
        assertEquals(0, FastMath.ceilLog2(-7));
        assertEquals(0, FastMath.ceilLog2(1));
        assertEquals(1, FastMath.ceilLog2(2));
        assertEquals(2, FastMath.ceilLog2(3));
        assertEquals(31, FastMath.ceilLog2(Integer.MAX_VALUE));
        assertEquals(-1, FastMath.floorLog2(0));
        assertEquals(-1, FastMath.floorLog2(-1));
        assertEquals(-1, FastMath.floorLog2(Integer.MIN_VALUE));
        assertEquals(0, FastMath.floorLog2(1));
        assertEquals(1, FastMath.floorLog2(3));
        assertEquals(30, FastMath.floorLog2(Integer.MAX_VALUE));
    }

    @Test
    void randomSourceKeepsItsContracts() {
        FastRandom a = new FastRandom(42L);
        FastRandom b = new FastRandom(42L);
        for (int i = 0; i < 100; i++) {
            assertEquals(a.nextLong(), b.nextLong());
        }
        a.setSeed(0L);
        b.setSeed(0L);
        assertEquals(a.nextLong(), b.nextLong());
        FastRandom random = new FastRandom(Long.MIN_VALUE);
        for (int i = 0; i < 10_000; i++) {
            float f = random.nextFloat();
            double d = random.nextDouble();
            assertTrue(f >= 0f && f < 1f);
            assertTrue(d >= 0.0 && d < 1.0);
            int bounded = random.nextInt(7);
            assertTrue(bounded >= 0 && bounded < 7);
            assertTrue(Double.isFinite(random.nextGaussian()));
        }
        assertEquals(1, random.nextInt(1) + 1);
        assertThrows(IllegalArgumentException.class, () -> random.nextInt(0));
        assertThrows(IllegalArgumentException.class, () -> random.nextInt(-5));
        assertEquals(0, random.nextInt(1));
        assertNotSame(new FastRandom(), new FastRandom());
        assertSame(FastRandom.particles(), FastRandom.particles());
    }

    @Test
    void gaussianSamplesLookNormal() {
        FastRandom random = new FastRandom(7L);
        double sum = 0;
        double squares = 0;
        int n = 200_000;
        for (int i = 0; i < n; i++) {
            double g = random.nextGaussian();
            sum += g;
            squares += g * g;
        }
        assertEquals(0.0, sum / n, 0.02);
        assertEquals(1.0, squares / n, 0.03);
    }
}
