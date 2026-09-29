package com.bdmajora.impetus.engine.impl.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PositionUtilTest {
    @Test
    void blockPackingRoundTripsWithSign() {
        int[][] samples = {{0, 0, 0}, {-1, -2048, -1}, {33554431, 2047, -33554432}, {123, 64, -456}};
        for (int[] s : samples) {
            long packed = PositionUtil.packBlock(s[0], s[1], s[2]);
            assertEquals(s[0], PositionUtil.unpackBlockX(packed));
            assertEquals(s[1], PositionUtil.unpackBlockY(packed));
            assertEquals(s[2], PositionUtil.unpackBlockZ(packed));
        }
    }

    @Test
    void chunkPackingMatchesVanillaLayout() {
        long key = PositionUtil.packChunk(-5, 7);
        assertEquals(-5, PositionUtil.unpackChunkX(key));
        assertEquals(7, PositionUtil.unpackChunkZ(key));
        assertEquals(((long) 7 << 32) | (0xFFFFFFFFL & -5), key);
    }

    @Test
    void sectionPackingRoundTripsWithSign() {
        int[][] samples = {{0, 0, 0}, {-1, -1, -1}, {2097151, 524287, -2097152}, {-30, 5, 31}};
        for (int[] s : samples) {
            long key = PositionUtil.packSection(s[0], s[1], s[2]);
            assertEquals(s[0], PositionUtil.unpackSectionX(key));
            assertEquals(s[1], PositionUtil.unpackSectionY(key));
            assertEquals(s[2], PositionUtil.unpackSectionZ(key));
        }
    }

    @Test
    void sectionCoordinateConversions() {
        assertEquals(1, PositionUtil.posToSectionCoord(17));
        assertEquals(-1, PositionUtil.posToSectionCoord(-1));
        assertEquals(-1, PositionUtil.posToSectionCoord(-0.5));
        assertEquals(2, PositionUtil.posToSectionCoord(47.9));
        assertEquals(35, PositionUtil.sectionToBlockCoord(2, 3));
        new PositionUtil();
    }
}
