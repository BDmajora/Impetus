package com.bdmajora.impetus.engine.impl.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BitwiseMathTest {
    @Test
    void lessThanIsOneOnlyWhenStrictlyLess() {
        assertEquals(1, BitwiseMath.lessThan(1, 2));
        assertEquals(0, BitwiseMath.lessThan(2, 2));
        assertEquals(0, BitwiseMath.lessThan(3, 2));
        assertEquals(1, BitwiseMath.lessThan(-5, 0));
    }

    @Test
    void greaterThanIsOneOnlyWhenStrictlyGreater() {
        assertEquals(1, BitwiseMath.greaterThan(3, 2));
        assertEquals(0, BitwiseMath.greaterThan(2, 2));
        assertEquals(0, BitwiseMath.greaterThan(1, 2));
        assertEquals(1, BitwiseMath.greaterThan(0, -5));
    }

    @Test
    void utilityClassIsConstructible() {
        new BitwiseMath();
    }
}
