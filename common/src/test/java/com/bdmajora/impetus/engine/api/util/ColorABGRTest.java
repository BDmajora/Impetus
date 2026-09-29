package com.bdmajora.impetus.engine.api.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorABGRTest {
    @Test
    void packsRedInTheLowByte() {
        int c = ColorABGR.pack(0x12, 0x34, 0x56, 0x78);
        assertEquals(0x78563412, c);
        assertEquals(0x12, ColorABGR.unpackRed(c));
        assertEquals(0x34, ColorABGR.unpackGreen(c));
        assertEquals(0x56, ColorABGR.unpackBlue(c));
        assertEquals(0x78, ColorABGR.unpackAlpha(c));
    }

    @Test
    void floatFormsNormalise() {
        assertEquals(0xFF00FF00, ColorABGR.pack(0f, 1f, 0f));
        assertEquals(0x80FF0000, ColorABGR.pack(0f, 0f, 1f, 128 / 255f));
        assertEquals(0x9A563412, ColorABGR.withAlpha(0x78563412, 0x9A));
        assertEquals(0xFF563412, ColorABGR.withAlpha(0x78563412, 1f));
        assertEquals(255, ColorU8.normalizedFloatToByte(1f));
        assertEquals(0, ColorU8.normalizedFloatToByte(0f));
        assertEquals(1f, ColorU8.byteToNormalizedFloat(255));
        new ColorABGR();
    }
}
