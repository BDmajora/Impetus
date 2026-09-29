package com.bdmajora.impetus.engine.impl.util.position;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SectionPosTest {
    @Test
    void boundsAreSixteenBlocksPerSection() {
        SectionPos pos = new SectionPos(1, -1, 2);
        assertEquals(16, pos.minX());
        assertEquals(-16, pos.minY());
        assertEquals(32, pos.minZ());
        assertEquals(31, pos.maxX());
        assertEquals(-1, pos.maxY());
        assertEquals(47, pos.maxZ());
        PositionalSupplier<Integer> supplier = (x, y, z) -> x + y + z;
        assertEquals(6, supplier.getAt(1, 2, 3));
    }
}
