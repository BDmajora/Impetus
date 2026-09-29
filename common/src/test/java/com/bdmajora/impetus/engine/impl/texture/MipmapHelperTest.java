package com.bdmajora.impetus.engine.impl.texture;

import com.bdmajora.impetus.engine.api.util.ColorARGB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MipmapHelperTest {
    @Test
    void averagesInLinearSpaceWeightedByAlpha() {
        int black = ColorARGB.pack(0, 0, 0, 255);
        int white = ColorARGB.pack(255, 255, 255, 255);
        int mid = MipmapHelper.weightedAverageColor(black, white);
        assertEquals(255, ColorARGB.unpackAlpha(mid));
        assertTrue(ColorARGB.unpackRed(mid) > 128);
        int clear = ColorARGB.pack(10, 20, 30, 0);
        int opaque = ColorARGB.pack(200, 100, 50, 200);
        assertEquals((opaque & 0xFFFFFF) | (50 << 24), MipmapHelper.weightedAverageColor(clear, opaque));
        assertEquals((opaque & 0xFFFFFF) | (50 << 24), MipmapHelper.weightedAverageColor(opaque, clear));
        int faint = ColorARGB.pack(0, 0, 0, 50);
        int weighted = MipmapHelper.weightedAverageColor(opaque, faint);
        assertEquals(125, ColorARGB.unpackAlpha(weighted));
        assertTrue(ColorARGB.unpackRed(weighted) > 128);
        new MipmapHelper();
    }
}
