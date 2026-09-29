package com.bdmajora.impetus.engine.api.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColorMixerTest {
    @Test
    void mixEndpointsReturnEachColour() {
        assertEquals(0x00FF00FF, ColorMixer.mix(0x00FF00FF, 0x00000000, 1f) | 0);
        assertEquals(0x00000000, ColorMixer.mix(0x00FF00FF, 0x00000000, 0f));
        int half = ColorMixer.mix(0xFFFFFFFF, 0x00000000, 0.5f);
        assertEquals(0x80, ColorABGR.unpackRed(half), 1);
        assertEquals(0x80, ColorABGR.unpackAlpha(half), 1);
    }

    @Test
    void multiplyScalesEachChannel() {
        assertEquals(0x7F7F7F7F, ColorMixer.mul(0xFFFFFFFF, 0x80808080));
        int rgb = ColorMixer.mulSingleWithoutAlpha(0xFFFFFFFF, 0x180);
        assertEquals(0xFF7F7F7F, rgb);
        assertEquals(0x00000000, ColorMixer.mul(0, 0xFFFFFFFF));
        new ColorMixer();
    }
}
