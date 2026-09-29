package com.bdmajora.impetus.engine.impl.util;

import com.bdmajora.impetus.engine.api.util.ColorARGB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelQuadUtilTest {
    @Test
    void vertexOffsetsAreEightInts() {
        assertEquals(0, ModelQuadUtil.vertexOffset(0));
        assertEquals(24, ModelQuadUtil.vertexOffset(3));
        new ModelQuadUtil();
    }

    @Test
    void bakedLightTakesBrighterChannelAndEmissionOnlyRaisesBlockLight() {
        assertEquals(0x00A000B0, ModelQuadUtil.mergeBakedLight(0, 0, 0x00A000B0));
        assertEquals(0x00F000F0, ModelQuadUtil.mergeBakedLight(0x00F00010, 0, 0x001000F0));
        assertEquals(0x001000FF, ModelQuadUtil.mergeBakedLight(0x00100000, 0xFF, 0x00000010));
    }

    @Test
    void colourMixingShortCircuitsOnWhite() {
        assertEquals(0x12345678, ModelQuadUtil.mixARGBColors(-1, 0x12345678));
        assertEquals(0x12345678, ModelQuadUtil.mixARGBColors(0x12345678, -1));
        int half = ColorARGB.pack(128, 128, 128, 128);
        int mixed = ModelQuadUtil.mixARGBColors(half, half);
        assertEquals(64, ColorARGB.unpackRed(mixed));
        assertEquals(64, ColorARGB.unpackGreen(mixed));
        assertEquals(64, ColorARGB.unpackBlue(mixed));
        assertEquals(64, ColorARGB.unpackAlpha(mixed));
    }
}
