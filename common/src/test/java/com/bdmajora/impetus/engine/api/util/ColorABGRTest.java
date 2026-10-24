package com.bdmajora.impetus.engine.api.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

class ColorABGRTest {
    // ints that sit on every sign and byte boundary
    private static final int[] EDGE_INTS = {
            0, 1, -1, 0xFF, 0x100, 0xFFFF, 0x10000, 0xFFFFFF, 0x1000000, 0x7F, 0x80, 0x7FFFFFFF, 0x80000000,
            0x80808080, 0x7F7F7F7F, 0x00FF00FF, 0xFF00FF00, 0x12345678, 0x87654321, 0xDEADBEEF, 0xFFFFFF00, 0x00FFFFFF
    };

    // floats outside, on and just inside the 0..1 contract
    private static final float[] EDGE_FLOATS = {
            0f, -0f, 1f, 0.5f, Float.MIN_VALUE, -Float.MIN_VALUE, Math.nextDown(1f), Math.nextUp(1f), 256 / 255f, 1.0039f,
            1.5f, 2f, -0.1f, -1f, Float.MAX_VALUE, -Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN
    };

    // reference model: every component's low byte at its ABGR offset
    private static int expected(int r, int g, int b, int a) {
        return (r & 0xFF) | (g & 0xFF) << 8 | (b & 0xFF) << 16 | (a & 0xFF) << 24;
    }

    // reference model of the float conversion: truncate, then clamp to 0..255 with NaN as 0
    private static int expectedByte(float value) {
        if (Float.isNaN(value)) {
            return 0;
        }
        float scaled = value * 255f;
        return scaled <= 0f ? 0 : scaled >= 255f ? 255 : (int) scaled;
    }

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
    void packOfExtremesFillsOrEmptiesTheWord() {
        assertEquals(0, ColorABGR.pack(0, 0, 0, 0));
        assertEquals(-1, ColorABGR.pack(255, 255, 255, 255));
        assertEquals(0x000000FF, ColorABGR.pack(255, 0, 0, 0));
        assertEquals(0x0000FF00, ColorABGR.pack(0, 255, 0, 0));
        assertEquals(0x00FF0000, ColorABGR.pack(0, 0, 255, 0));
        assertEquals(0xFF000000, ColorABGR.pack(0, 0, 0, 255));
        // alpha 0x80 sets the sign bit; the packed word is negative
        assertTrue(ColorABGR.pack(0, 0, 0, 0x80) < 0);
    }

    @Test
    void outOfRangeIntComponentsKeepOnlyTheirLowByteAndNeverBleed() {
        // every channel alone, over values below, inside and above 0..255
        for (int v = -1024; v <= 1024; v++) {
            assertEquals(v & 0xFF, ColorABGR.pack(v, 0, 0, 0), "red " + v);
            assertEquals((v & 0xFF) << 8, ColorABGR.pack(0, v, 0, 0), "green " + v);
            assertEquals((v & 0xFF) << 16, ColorABGR.pack(0, 0, v, 0), "blue " + v);
            assertEquals((v & 0xFF) << 24, ColorABGR.pack(0, 0, 0, v), "alpha " + v);
        }
        for (int v : EDGE_INTS) {
            assertEquals(expected(v, 0, 0, 0), ColorABGR.pack(v, 0, 0, 0));
            assertEquals(expected(0, v, 0, 0), ColorABGR.pack(0, v, 0, 0));
            assertEquals(expected(0, 0, v, 0), ColorABGR.pack(0, 0, v, 0));
            assertEquals(expected(0, 0, 0, v), ColorABGR.pack(0, 0, 0, v));
            assertEquals(expected(v, v, v, v), ColorABGR.pack(v, v, v, v));
        }
        assertEquals(0xFFFFFFFF, ColorABGR.pack(-1, Integer.MAX_VALUE, 0x1FF, Integer.MIN_VALUE | 0xFF));
        assertEquals(0, ColorABGR.pack(256, Integer.MIN_VALUE, 0x100, 0x7FFFFF00));
    }

    @Test
    void packMatchesTheModelForRandomComponents() {
        SplittableRandom random = new SplittableRandom(0xAB6E);
        for (int i = 0; i < 100_000; i++) {
            int r = random.nextInt(), g = random.nextInt(), b = random.nextInt(), a = random.nextInt();
            assertEquals(expected(r, g, b, a), ColorABGR.pack(r, g, b, a));
        }
    }

    @Test
    void everyByteRoundTripsThroughPackAndUnpack() {
        for (int v = 0; v <= 255; v++) {
            int c = ColorABGR.pack(v, 255 - v, v ^ 0x5A, (v * 7) & 0xFF);
            assertEquals(v, ColorABGR.unpackRed(c));
            assertEquals(255 - v, ColorABGR.unpackGreen(c));
            assertEquals(v ^ 0x5A, ColorABGR.unpackBlue(c));
            assertEquals((v * 7) & 0xFF, ColorABGR.unpackAlpha(c));
        }
    }

    @Test
    void unpackReadsOnlyItsOwnByteWithoutSignExtension() {
        assertEquals(0, ColorABGR.unpackRed(0));
        assertEquals(0, ColorABGR.unpackAlpha(0));
        assertEquals(255, ColorABGR.unpackRed(-1));
        assertEquals(255, ColorABGR.unpackGreen(-1));
        assertEquals(255, ColorABGR.unpackBlue(-1));
        assertEquals(255, ColorABGR.unpackAlpha(-1));
        // sign bit alone: alpha is 0x80, not a negative sign-extended value
        assertEquals(0x80, ColorABGR.unpackAlpha(Integer.MIN_VALUE));
        assertEquals(0, ColorABGR.unpackRed(Integer.MIN_VALUE));
        assertEquals(0, ColorABGR.unpackGreen(Integer.MIN_VALUE));
        assertEquals(0, ColorABGR.unpackBlue(Integer.MIN_VALUE));
        assertEquals(0x7F, ColorABGR.unpackAlpha(Integer.MAX_VALUE));
        assertEquals(0xFF, ColorABGR.unpackBlue(Integer.MAX_VALUE));
        for (int shift = 0; shift < 32; shift += 8) {
            for (int v = 0; v <= 255; v++) {
                int c = v << shift;
                assertEquals(shift == 0 ? v : 0, ColorABGR.unpackRed(c));
                assertEquals(shift == 8 ? v : 0, ColorABGR.unpackGreen(c));
                assertEquals(shift == 16 ? v : 0, ColorABGR.unpackBlue(c));
                assertEquals(shift == 24 ? v : 0, ColorABGR.unpackAlpha(c));
            }
        }
    }

    @Test
    void anyWordRoundTripsThroughUnpackAndPack() {
        SplittableRandom random = new SplittableRandom(0xC0102);
        for (int i = 0; i < 100_000; i++) {
            int c = random.nextInt();
            assertEquals(c, ColorABGR.pack(ColorABGR.unpackRed(c), ColorABGR.unpackGreen(c), ColorABGR.unpackBlue(c), ColorABGR.unpackAlpha(c)));
        }
        for (int c : EDGE_INTS) {
            assertEquals(c, ColorABGR.pack(ColorABGR.unpackRed(c), ColorABGR.unpackGreen(c), ColorABGR.unpackBlue(c), ColorABGR.unpackAlpha(c)));
            int r = ColorABGR.unpackRed(c), g = ColorABGR.unpackGreen(c), b = ColorABGR.unpackBlue(c), a = ColorABGR.unpackAlpha(c);
            assertTrue(r >= 0 && r <= 255 && g >= 0 && g <= 255 && b >= 0 && b <= 255 && a >= 0 && a <= 255, Integer.toHexString(c));
        }
    }

    @Test
    void intWithAlphaReplacesOnlyTheTopByte() {
        assertEquals(0x9A563412, ColorABGR.withAlpha(0x78563412, 0x9A));
        assertEquals(0x00FFFFFF, ColorABGR.withAlpha(-1, 0));
        assertEquals(0xFF000000, ColorABGR.withAlpha(0, 255));
        assertEquals(0x80000000, ColorABGR.withAlpha(0, 0x80));
        for (int rgb : EDGE_INTS) {
            for (int alpha = -1024; alpha <= 1024; alpha++) {
                assertEquals((rgb & 0x00FFFFFF) | (alpha & 0xFF) << 24, ColorABGR.withAlpha(rgb, alpha));
            }
        }
    }

    @Test
    void outOfRangeIntAlphaKeepsItsLowByteLikePack() {
        assertEquals(0x00563412, ColorABGR.withAlpha(0x78563412, 256));
        assertEquals(0xFF563412, ColorABGR.withAlpha(0x78563412, -1));
        assertEquals(0x34563412, ColorABGR.withAlpha(0x78563412, 0x1234));
        assertEquals(0x00563412, ColorABGR.withAlpha(0x78563412, Integer.MIN_VALUE));
        assertEquals(0xFF563412, ColorABGR.withAlpha(0x78563412, Integer.MAX_VALUE));
        SplittableRandom random = new SplittableRandom(0xA1FA);
        for (int i = 0; i < 100_000; i++) {
            int rgb = random.nextInt(), alpha = random.nextInt();
            assertEquals(ColorABGR.pack(ColorABGR.unpackRed(rgb), ColorABGR.unpackGreen(rgb), ColorABGR.unpackBlue(rgb), alpha), ColorABGR.withAlpha(rgb, alpha));
        }
    }

    @Test
    void floatFormsNormalise() {
        assertEquals(0xFF00FF00, ColorABGR.pack(0f, 1f, 0f));
        assertEquals(0x80FF0000, ColorABGR.pack(0f, 0f, 1f, 128 / 255f));
        assertEquals(0xFF563412, ColorABGR.withAlpha(0x78563412, 1f));
        assertEquals(0xFF000000, ColorABGR.pack(0f, 0f, 0f));
        assertEquals(-1, ColorABGR.pack(1f, 1f, 1f));
        assertEquals(0, ColorABGR.pack(0f, 0f, 0f, 0f));
        assertEquals(-1, ColorABGR.pack(1f, 1f, 1f, 1f));
        assertEquals(255, ColorU8.normalizedFloatToByte(1f));
        assertEquals(0, ColorU8.normalizedFloatToByte(0f));
        assertEquals(1f, ColorU8.byteToNormalizedFloat(255));
        assertEquals(0f, ColorU8.byteToNormalizedFloat(0));
    }

    @Test
    void floatConversionTruncatesInsideTheRange() {
        // truncation, as vanilla's (int) (f * 255); pinned so a switch to rounding is a deliberate change
        assertEquals(127, ColorU8.normalizedFloatToByte(0.5f));
        assertEquals(254, ColorU8.normalizedFloatToByte(Math.nextDown(1f)));
        assertEquals(0, ColorU8.normalizedFloatToByte(Float.MIN_VALUE));
        assertEquals(0, ColorU8.normalizedFloatToByte(-0f));
        assertEquals(0, ColorU8.normalizedFloatToByte(1 / 256f));
        assertEquals(1, ColorU8.normalizedFloatToByte(1 / 255f));
        assertEquals(0x7F563412, ColorABGR.withAlpha(0x78563412, 0.5f));
        // strided walk over every float bit pattern in 0..1
        int one = Float.floatToRawIntBits(1f);
        for (int bits = 0; bits <= one; bits += 997) {
            float f = Float.intBitsToFloat(bits);
            assertEquals((int) (f * 255f), ColorU8.normalizedFloatToByte(f), Float.toString(f));
        }
    }

    @Test
    void everyByteRoundTripsThroughItsNormalisedFloat() {
        for (int v = 0; v <= 255; v++) {
            assertEquals(v, ColorU8.normalizedFloatToByte(ColorU8.byteToNormalizedFloat(v)), "via inverse " + v);
            assertEquals(v, ColorU8.normalizedFloatToByte(v / 255f), "via division " + v);
            int w = 255 - v;
            assertEquals(ColorABGR.pack(v, w, v ^ 0x5A, (v * 7) & 0xFF),
                    ColorABGR.pack(v / 255f, w / 255f, (v ^ 0x5A) / 255f, ((v * 7) & 0xFF) / 255f));
            assertEquals(ColorABGR.pack(v, w, v ^ 0x5A, 255), ColorABGR.pack(v / 255f, w / 255f, (v ^ 0x5A) / 255f));
            assertEquals(ColorABGR.withAlpha(0x00123456, v), ColorABGR.withAlpha(0x00123456, v / 255f));
            if (v > 0) {
                assertTrue(ColorU8.byteToNormalizedFloat(v) > ColorU8.byteToNormalizedFloat(v - 1));
            }
        }
    }

    @Test
    void outOfRangeFloatComponentsClampInsteadOfWrapping() {
        // just past 1.0 wrapped to 0 (black / fully occluded), 1.5 to 126, -0.1 to 231
        assertEquals(255, ColorU8.normalizedFloatToByte(256 / 255f));
        assertEquals(255, ColorU8.normalizedFloatToByte(1.5f));
        assertEquals(255, ColorU8.normalizedFloatToByte(2f));
        assertEquals(255, ColorU8.normalizedFloatToByte(Float.MAX_VALUE));
        assertEquals(0, ColorU8.normalizedFloatToByte(-0.1f));
        assertEquals(0, ColorU8.normalizedFloatToByte(-1f));
        assertEquals(0, ColorU8.normalizedFloatToByte(-Float.MAX_VALUE));
        assertEquals(255, ColorU8.normalizedFloatToByte(Math.nextUp(1f)));
        assertEquals(0, ColorU8.normalizedFloatToByte(-Float.MIN_VALUE));
        for (float f = 1f; f <= 64f; f += 1 / 512f) {
            assertEquals(255, ColorU8.normalizedFloatToByte(f), Float.toString(f));
            assertEquals(0, ColorU8.normalizedFloatToByte(-f), Float.toString(-f));
        }
    }

    @Test
    void nonFiniteFloatComponentsSaturate() {
        assertEquals(0, ColorU8.normalizedFloatToByte(Float.NaN));
        assertEquals(255, ColorU8.normalizedFloatToByte(Float.POSITIVE_INFINITY));
        assertEquals(0, ColorU8.normalizedFloatToByte(Float.NEGATIVE_INFINITY));
        assertEquals(0xFF000000, ColorABGR.pack(Float.NaN, Float.NaN, Float.NaN));
        assertEquals(0, ColorABGR.pack(Float.NaN, Float.NaN, Float.NaN, Float.NaN));
        assertEquals(-1, ColorABGR.pack(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY));
        assertEquals(0, ColorABGR.pack(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY));
        assertEquals(0x00563412, ColorABGR.withAlpha(0x78563412, Float.NaN));
        assertEquals(0xFF563412, ColorABGR.withAlpha(0x78563412, Float.POSITIVE_INFINITY));
        assertEquals(0x00563412, ColorABGR.withAlpha(0x78563412, Float.NEGATIVE_INFINITY));
    }

    @Test
    void floatConversionStaysInRangeAndNeverDecreases() {
        int previous = -1;
        for (float f = -4f; f <= 4f; f += 1 / 4096f) {
            int b = ColorU8.normalizedFloatToByte(f);
            assertTrue(b >= 0 && b <= 255, Float.toString(f));
            assertTrue(b >= previous, "dropped at " + f);
            previous = b;
        }
        for (float f : EDGE_FLOATS) {
            assertEquals(expectedByte(f), ColorU8.normalizedFloatToByte(f), Float.toString(f));
        }
    }

    @Test
    void eachFloatChannelClampsIndependently() {
        assertEquals(0xFF7F00FF, ColorABGR.pack(2f, -1f, 0.5f, 3f));
        assertEquals(0x00FF7F00, ColorABGR.pack(-5f, 0.5f, 256 / 255f, -0.01f));
        for (float f : EDGE_FLOATS) {
            int e = expectedByte(f);
            assertEquals(expected(e, 0, 0, 0), ColorABGR.pack(f, 0f, 0f, 0f), "red " + f);
            assertEquals(expected(0, e, 0, 0), ColorABGR.pack(0f, f, 0f, 0f), "green " + f);
            assertEquals(expected(0, 0, e, 0), ColorABGR.pack(0f, 0f, f, 0f), "blue " + f);
            assertEquals(expected(0, 0, 0, e), ColorABGR.pack(0f, 0f, 0f, f), "alpha " + f);
            assertEquals(expected(e, e, e, 255), ColorABGR.pack(f, f, f), "opaque " + f);
            for (float g : EDGE_FLOATS) {
                assertEquals(expected(e, expectedByte(g), e, expectedByte(g)), ColorABGR.pack(f, g, f, g));
            }
        }
    }

    @Test
    void opaqueFloatPackAlwaysSetsAlphaToFull() {
        SplittableRandom random = new SplittableRandom(0x0FA9E);
        for (int i = 0; i < 100_000; i++) {
            float r = (float) random.nextDouble(-2, 3), g = (float) random.nextDouble(-2, 3), b = (float) random.nextDouble(-2, 3);
            int c = ColorABGR.pack(r, g, b);
            assertEquals(255, ColorABGR.unpackAlpha(c));
            assertEquals(ColorABGR.pack(r, g, b, 1f), c);
            assertEquals(expected(expectedByte(r), expectedByte(g), expectedByte(b), 255), c);
        }
    }

    @Test
    void floatWithAlphaClampsAndKeepsRgb() {
        // SEPARATE_AO writes AO through here per vertex; an AO a hair over 1 made the vertex fully occluded
        assertEquals(0xFF563412, ColorABGR.withAlpha(0x78563412, 256 / 255f));
        assertEquals(0xFF563412, ColorABGR.withAlpha(0x78563412, 2f));
        assertEquals(0x00563412, ColorABGR.withAlpha(0x78563412, -0.1f));
        assertEquals(0x00563412, ColorABGR.withAlpha(0x78563412, 0f));
        for (int rgb : EDGE_INTS) {
            for (float f : EDGE_FLOATS) {
                assertEquals((rgb & 0x00FFFFFF) | expectedByte(f) << 24, ColorABGR.withAlpha(rgb, f), Integer.toHexString(rgb) + " " + f);
            }
        }
    }

    @Test
    void holderIsFinalWithAPrivateConstructor() throws ReflectiveOperationException {
        assertTrue(Modifier.isFinal(ColorABGR.class.getModifiers()));
        Constructor<?>[] constructors = ColorABGR.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        // still constructible reflectively, the body does nothing
        constructors[0].setAccessible(true);
        assertNotNull(constructors[0].newInstance());
    }
}
