package com.bdmajora.impetus.engine.api.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorARGBTest {
    @Test
    void packAndUnpackComponents() {
        int c = ColorARGB.pack(0x12, 0x34, 0x56, 0x78);
        assertEquals(0x78123456, c);
        assertEquals(0x78, ColorARGB.unpackAlpha(c));
        assertEquals(0x12, ColorARGB.unpackRed(c));
        assertEquals(0x34, ColorARGB.unpackGreen(c));
        assertEquals(0x56, ColorARGB.unpackBlue(c));
        assertEquals(0xFF123456, ColorARGB.pack(0x12, 0x34, 0x56));
    }

    @Test
    void abgrConversionsSwapRedAndBlue() {
        int c = 0x78123456;
        assertEquals(0x78563412, ColorARGB.toABGR(c));
        assertEquals(0x9A563412, ColorARGB.toABGR(c, 0x9A));
        assertEquals(0x9A123456, ColorARGB.withAlpha(c, 0x9A));
        new ColorARGB();
    }
}
