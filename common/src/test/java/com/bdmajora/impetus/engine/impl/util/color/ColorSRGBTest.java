package com.bdmajora.impetus.engine.impl.util.color;

import com.bdmajora.impetus.engine.api.util.ColorARGB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorSRGBTest {
    @Test
    void tableEndpointsMatchTheStandard() {
        assertEquals(0f, ColorSRGB.srgbToLinear(0));
        assertEquals(1f, ColorSRGB.srgbToLinear(255));
        assertEquals(1f, ColorSRGB.srgbToLinear(511));
    }

    @Test
    void linearToSrgbRoundTripsAndClamps() {
        int packed = ColorSRGB.linearToSrgb(0f, 1f, ColorSRGB.srgbToLinear(128), 0x80);
        assertEquals(0, ColorARGB.unpackRed(packed));
        assertEquals(255, ColorARGB.unpackGreen(packed));
        assertEquals(128, ColorARGB.unpackBlue(packed), 1);
        assertEquals(0x80, ColorARGB.unpackAlpha(packed));
        int clamped = ColorSRGB.linearToSrgb(-1f, 2f, Float.NaN, 255);
        assertEquals(0, ColorARGB.unpackRed(clamped));
        assertEquals(255, ColorARGB.unpackGreen(clamped));
        assertEquals(0, ColorARGB.unpackBlue(clamped));
        new ColorSRGB();
    }
}
