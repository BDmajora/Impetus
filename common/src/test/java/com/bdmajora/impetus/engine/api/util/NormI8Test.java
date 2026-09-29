package com.bdmajora.impetus.engine.api.util;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NormI8Test {
    @Test
    void packAndUnpackRoundTripWithinPrecision() {
        int packed = NormI8.pack(0.5f, -0.25f, 1.0f);
        assertEquals(0.5f, NormI8.unpackX(packed), 0.01f);
        assertEquals(-0.25f, NormI8.unpackY(packed), 0.01f);
        assertEquals(1.0f, NormI8.unpackZ(packed), 0.01f);
        assertEquals(0.0f, NormI8.unpackW(packed), 0.01f);
        assertEquals(packed, NormI8.pack(new Vector3f(0.5f, -0.25f, 1.0f)));
    }

    @Test
    void tangentHandednessLivesInTopByte() {
        int packed = NormI8.pack(0, 0, 1, -1);
        assertEquals(-1.0f, NormI8.unpackW(packed), 0.01f);
        assertEquals(1.0f, NormI8.unpackZ(packed), 0.01f);
        assertEquals(1.0f, NormI8.unpackX(NormI8.pack(5f, 0, 0)), 0.01f);
        new NormI8();
    }
}
