package com.bdmajora.impetus.engine.impl.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MathUtilTest {
    @Test
    void powerOfTwoRejectsZeroAndNegatives() {
        assertTrue(MathUtil.isPowerOfTwo(1));
        assertTrue(MathUtil.isPowerOfTwo(1024));
        assertFalse(MathUtil.isPowerOfTwo(0));
        assertFalse(MathUtil.isPowerOfTwo(-2));
        assertFalse(MathUtil.isPowerOfTwo(12));
    }

    @Test
    void roundingAndUnits() {
        assertEquals(3, MathUtil.toMib(3L << 20));
        assertEquals(16, MathUtil.roundToward(9, 8));
        assertEquals(8, MathUtil.roundToward(8, 8));
        assertEquals(-8, MathUtil.roundToward(-9, 8));
        assertEquals(9f, MathUtil.square(3f));
    }

    @Test
    void mojangFloorHandlesNegatives() {
        assertEquals(-2, MathUtil.mojfloor(-1.5f));
        assertEquals(1, MathUtil.mojfloor(1.5f));
        assertEquals(-1, MathUtil.mojfloor(-1.0f));
        assertEquals(-2, MathUtil.mojfloor(-1.5));
        assertEquals(1, MathUtil.mojfloor(1.9));
    }

    @Test
    void clampsAndComparisons() {
        assertEquals(5, MathUtil.clamp(9, 0, 5));
        assertEquals(0, MathUtil.clamp(-9, 0, 5));
        assertEquals(2.5f, MathUtil.clamp(2.5f, 0f, 5f));
        assertEquals(0f, MathUtil.saturate(-1f));
        assertEquals(1f, MathUtil.saturate(2f));
        assertEquals(0.5f, MathUtil.saturate(0.5f));
        assertTrue(MathUtil.roughlyEqual(1f, 1.000001f));
        assertFalse(MathUtil.roughlyEqual(1f, 1.1f));
        new MathUtil();
    }
}
