package com.bdmajora.impetus.engine.impl.util.rand;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RandomTest {
    @Test
    void splitMixIsDeterministicPerSeed() {
        SplitMixRandom a = new SplitMixRandom(42);
        SplitMixRandom b = new SplitMixRandom(42);
        assertEquals(a.nextLong(), b.nextLong());
        assertEquals(a.nextInt(), b.nextInt());
        assertEquals(a.nextDouble(), b.nextDouble());
        assertEquals(a.nextFloat(), b.nextFloat());
        assertEquals(a.nextBoolean(), b.nextBoolean());
        byte[] x = new byte[11], y = new byte[11];
        a.nextBytes(x);
        b.nextBytes(y);
        assertArrayEquals(x, y);
        a.setState(7);
        b.setState(7);
        assertEquals(a.nextLong(), b.nextLong());
        new SplitMixRandom();
    }

    @Test
    void splitMixBoundedValuesStayInRange() {
        SplitMixRandom r = new SplitMixRandom(1);
        for (int i = 0; i < 200; i++) {
            int v = r.nextInt(10);
            assertTrue(v >= 0 && v < 10);
            long w = r.nextLong(16);
            assertTrue(w >= 0 && w < 16);
            long big = r.nextLong(Long.MAX_VALUE - 1);
            assertTrue(big >= 0);
        }
        assertThrows(IllegalArgumentException.class, () -> r.nextLong(0));
    }

    @Test
    void xoroshiroIsDeterministicAndReseedable() {
        XoRoShiRoRandom a = new XoRoShiRoRandom(99);
        long first = a.nextLong();
        a.nextInt();
        a.nextDouble();
        a.nextFloat();
        a.nextBoolean();
        a.nextBytes(new byte[9]);
        a.setSeed(99);
        assertEquals(first, a.nextLong());
        assertSame(a, a.setSeedAndReturn(5));
        a.setSeed(99);
        assertEquals(first, a.nextLong());
        assertNotEquals(new XoRoShiRoRandom().nextLong(), new XoRoShiRoRandom().nextLong());
        assertNotEquals(XoRoShiRoRandom.randomSeed(), XoRoShiRoRandom.randomSeed());
    }

    @Test
    void xoroshiroBoundedValuesStayInRange() {
        XoRoShiRoRandom r = new XoRoShiRoRandom(3);
        for (int i = 0; i < 200; i++) {
            assertTrue(r.nextInt(7) < 7);
            assertTrue(r.nextLong(64) < 64);
            assertTrue(r.nextLong(Long.MAX_VALUE - 1) >= 0);
            assertTrue(r.nextDouble() < 1.0);
            assertTrue(r.nextFloat() < 1.0f);
        }
        assertThrows(IllegalArgumentException.class, () -> r.nextLong(-1));
    }
}
