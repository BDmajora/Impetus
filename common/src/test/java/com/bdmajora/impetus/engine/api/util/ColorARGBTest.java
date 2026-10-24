package com.bdmajora.impetus.engine.api.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

class ColorARGBTest {
    // ints that sit on every sign and byte boundary
    private static final int[] EDGE_INTS = {
            0, 1, -1, 0xFF, 0x100, 0xFFFF, 0x10000, 0xFFFFFF, 0x1000000, 0x7F, 0x80, 0x7FFFFFFF, 0x80000000,
            0x80808080, 0x7F7F7F7F, 0x00FF00FF, 0xFF00FF00, 0x12345678, 0x87654321, 0xDEADBEEF, 0xFFFFFF00, 0x00FFFFFF
    };

    // reference model: every component's low byte at its ARGB offset
    private static int expected(int r, int g, int b, int a) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    // reference model of the GL repack: the same channels laid out by ColorABGR
    private static int expectedAbgr(int argb, int alpha) {
        return ColorABGR.pack(ColorARGB.unpackRed(argb), ColorARGB.unpackGreen(argb), ColorARGB.unpackBlue(argb), alpha);
    }

    @Test
    void packPutsAlphaInTheHighByteAndBlueInTheLow() {
        int c = ColorARGB.pack(0x12, 0x34, 0x56, 0x78);
        assertEquals(0x78123456, c);
        assertEquals(0x78, ColorARGB.unpackAlpha(c));
        assertEquals(0x12, ColorARGB.unpackRed(c));
        assertEquals(0x34, ColorARGB.unpackGreen(c));
        assertEquals(0x56, ColorARGB.unpackBlue(c));
        assertEquals(0xFF123456, ColorARGB.pack(0x12, 0x34, 0x56));
    }

    @Test
    void packOfExtremesFillsOrEmptiesTheWord() {
        assertEquals(0, ColorARGB.pack(0, 0, 0, 0));
        assertEquals(-1, ColorARGB.pack(255, 255, 255, 255));
        assertEquals(0x00FF0000, ColorARGB.pack(255, 0, 0, 0));
        assertEquals(0x0000FF00, ColorARGB.pack(0, 255, 0, 0));
        assertEquals(0x000000FF, ColorARGB.pack(0, 0, 255, 0));
        assertEquals(0xFF000000, ColorARGB.pack(0, 0, 0, 255));
        assertEquals(0xFF000000, ColorARGB.pack(0, 0, 0));
        assertEquals(-1, ColorARGB.pack(255, 255, 255));
        // alpha 0x80 sets the sign bit; the packed word is negative
        assertTrue(ColorARGB.pack(0, 0, 0, 0x80) < 0);
    }

    @Test
    void outOfRangeComponentsKeepOnlyTheirLowByteAndNeverBleed() {
        // every channel alone, over values below, inside and above 0..255
        for (int v = -1024; v <= 1024; v++) {
            assertEquals((v & 0xFF) << 16, ColorARGB.pack(v, 0, 0, 0), "red " + v);
            assertEquals((v & 0xFF) << 8, ColorARGB.pack(0, v, 0, 0), "green " + v);
            assertEquals(v & 0xFF, ColorARGB.pack(0, 0, v, 0), "blue " + v);
            assertEquals((v & 0xFF) << 24, ColorARGB.pack(0, 0, 0, v), "alpha " + v);
        }
        for (int v : EDGE_INTS) {
            assertEquals(expected(v, 0, 0, 0), ColorARGB.pack(v, 0, 0, 0));
            assertEquals(expected(0, v, 0, 0), ColorARGB.pack(0, v, 0, 0));
            assertEquals(expected(0, 0, v, 0), ColorARGB.pack(0, 0, v, 0));
            assertEquals(expected(0, 0, 0, v), ColorARGB.pack(0, 0, 0, v));
            assertEquals(expected(v, v, v, v), ColorARGB.pack(v, v, v, v));
        }
        assertEquals(0xFFFFFFFF, ColorARGB.pack(-1, Integer.MAX_VALUE, 0x1FF, Integer.MIN_VALUE | 0xFF));
        assertEquals(0, ColorARGB.pack(256, Integer.MIN_VALUE, 0x100, 0x7FFFFF00));
        // BoxBlur and ColorSRGB hand over sums and rounded floats; a 300 blue must not carry into green
        assertEquals(0xFF00002C, ColorARGB.pack(0, 0, 300, 255));
    }

    @Test
    void packMatchesTheModelForRandomComponents() {
        SplittableRandom random = new SplittableRandom(0xA6B);
        for (int i = 0; i < 100_000; i++) {
            int r = random.nextInt(), g = random.nextInt(), b = random.nextInt(), a = random.nextInt();
            assertEquals(expected(r, g, b, a), ColorARGB.pack(r, g, b, a));
        }
    }

    @Test
    void opaquePackAlwaysSetsAlphaToFull() {
        for (int v : EDGE_INTS) {
            assertEquals(expected(v, v, v, 255), ColorARGB.pack(v, v, v));
            assertEquals(255, ColorARGB.unpackAlpha(ColorARGB.pack(v, 0, 0)));
        }
        SplittableRandom random = new SplittableRandom(0x0A9E);
        for (int i = 0; i < 100_000; i++) {
            int r = random.nextInt(), g = random.nextInt(), b = random.nextInt();
            assertEquals(ColorARGB.pack(r, g, b, 255), ColorARGB.pack(r, g, b));
        }
    }

    @Test
    void everyByteRoundTripsThroughPackAndUnpack() {
        for (int v = 0; v <= 255; v++) {
            int c = ColorARGB.pack(v, 255 - v, v ^ 0x5A, (v * 7) & 0xFF);
            assertEquals(v, ColorARGB.unpackRed(c));
            assertEquals(255 - v, ColorARGB.unpackGreen(c));
            assertEquals(v ^ 0x5A, ColorARGB.unpackBlue(c));
            assertEquals((v * 7) & 0xFF, ColorARGB.unpackAlpha(c));
        }
    }

    @Test
    void unpackReadsOnlyItsOwnByteWithoutSignExtension() {
        assertEquals(0, ColorARGB.unpackAlpha(0));
        assertEquals(0, ColorARGB.unpackBlue(0));
        assertEquals(255, ColorARGB.unpackAlpha(-1));
        assertEquals(255, ColorARGB.unpackRed(-1));
        assertEquals(255, ColorARGB.unpackGreen(-1));
        assertEquals(255, ColorARGB.unpackBlue(-1));
        // sign bit alone: alpha is 0x80, not a negative sign-extended value
        assertEquals(0x80, ColorARGB.unpackAlpha(Integer.MIN_VALUE));
        assertEquals(0, ColorARGB.unpackRed(Integer.MIN_VALUE));
        assertEquals(0, ColorARGB.unpackGreen(Integer.MIN_VALUE));
        assertEquals(0, ColorARGB.unpackBlue(Integer.MIN_VALUE));
        assertEquals(0x7F, ColorARGB.unpackAlpha(Integer.MAX_VALUE));
        assertEquals(0xFF, ColorARGB.unpackRed(Integer.MAX_VALUE));
        for (int shift = 0; shift < 32; shift += 8) {
            for (int v = 0; v <= 255; v++) {
                int c = v << shift;
                assertEquals(shift == 0 ? v : 0, ColorARGB.unpackBlue(c));
                assertEquals(shift == 8 ? v : 0, ColorARGB.unpackGreen(c));
                assertEquals(shift == 16 ? v : 0, ColorARGB.unpackRed(c));
                assertEquals(shift == 24 ? v : 0, ColorARGB.unpackAlpha(c));
            }
        }
    }

    @Test
    void anyWordRoundTripsThroughUnpackAndPack() {
        SplittableRandom random = new SplittableRandom(0xC0103);
        for (int i = 0; i < 100_000; i++) {
            int c = random.nextInt();
            assertEquals(c, ColorARGB.pack(ColorARGB.unpackRed(c), ColorARGB.unpackGreen(c), ColorARGB.unpackBlue(c), ColorARGB.unpackAlpha(c)));
        }
        for (int c : EDGE_INTS) {
            int r = ColorARGB.unpackRed(c), g = ColorARGB.unpackGreen(c), b = ColorARGB.unpackBlue(c), a = ColorARGB.unpackAlpha(c);
            assertEquals(c, ColorARGB.pack(r, g, b, a));
            assertTrue(r >= 0 && r <= 255 && g >= 0 && g <= 255 && b >= 0 && b <= 255 && a >= 0 && a <= 255, Integer.toHexString(c));
        }
    }

    @Test
    void toABGRSwapsRedAndBlueAndKeepsAlphaAndGreen() {
        assertEquals(0x78563412, ColorARGB.toABGR(0x78123456));
        assertEquals(0, ColorARGB.toABGR(0));
        assertEquals(-1, ColorARGB.toABGR(-1));
        assertEquals(0x000000FF, ColorARGB.toABGR(0x00FF0000));
        assertEquals(0x00FF0000, ColorARGB.toABGR(0x000000FF));
        assertEquals(0x0000FF00, ColorARGB.toABGR(0x0000FF00));
        assertEquals(0xFF000000, ColorARGB.toABGR(0xFF000000));
        assertEquals(Integer.MIN_VALUE, ColorARGB.toABGR(Integer.MIN_VALUE));
        assertEquals(0x7FFFFFFF, ColorARGB.toABGR(Integer.MAX_VALUE));
        for (int c : EDGE_INTS) {
            assertEquals(expectedAbgr(c, ColorARGB.unpackAlpha(c)), ColorARGB.toABGR(c), Integer.toHexString(c));
        }
        SplittableRandom random = new SplittableRandom(0xAB6A);
        for (int i = 0; i < 100_000; i++) {
            int c = random.nextInt();
            int abgr = ColorARGB.toABGR(c);
            assertEquals(ColorARGB.unpackRed(c), ColorABGR.unpackRed(abgr));
            assertEquals(ColorARGB.unpackGreen(c), ColorABGR.unpackGreen(abgr));
            assertEquals(ColorARGB.unpackBlue(c), ColorABGR.unpackBlue(abgr));
            assertEquals(ColorARGB.unpackAlpha(c), ColorABGR.unpackAlpha(abgr));
        }
    }

    @Test
    void toABGRIsItsOwnInverse() {
        // the R/B swap reads the same both ways, so applying it twice is the identity
        for (int c : EDGE_INTS) {
            assertEquals(c, ColorARGB.toABGR(ColorARGB.toABGR(c)));
        }
        SplittableRandom random = new SplittableRandom(0x1A7);
        for (int i = 0; i < 100_000; i++) {
            int c = random.nextInt();
            assertEquals(c, ColorARGB.toABGR(ColorARGB.toABGR(c)));
        }
    }

    @Test
    void toABGRWithAlphaReplacesTheColoursOwnAlpha() {
        assertEquals(0x9A563412, ColorARGB.toABGR(0x78123456, 0x9A));
        assertEquals(0x00563412, ColorARGB.toABGR(0x78123456, 0));
        assertEquals(0xFF563412, ColorARGB.toABGR(0x00123456, 255));
        assertEquals(0xFF000000, ColorARGB.toABGR(0, 255));
        assertEquals(0x00FFFFFF, ColorARGB.toABGR(-1, 0));
        for (int c : EDGE_INTS) {
            assertEquals(ColorARGB.toABGR(c), ColorARGB.toABGR(c, ColorARGB.unpackAlpha(c)));
            for (int alpha = 0; alpha <= 255; alpha++) {
                assertEquals(expectedAbgr(c, alpha), ColorARGB.toABGR(c, alpha));
                assertEquals(ColorARGB.toABGR(ColorARGB.withAlpha(c, alpha)), ColorARGB.toABGR(c, alpha));
            }
        }
    }

    @Test
    void outOfRangeAlphaInToABGRKeepsItsLowByteLikePack() {
        // the alpha was ORed in unmasked, so its upper bits landed on blue, green and red
        assertEquals(0x00563412, ColorARGB.toABGR(0x78123456, 256));
        assertEquals(0xFF563412, ColorARGB.toABGR(0x78123456, -1));
        assertEquals(0x34563412, ColorARGB.toABGR(0x78123456, 0x1234));
        assertEquals(0x00563412, ColorARGB.toABGR(0x78123456, Integer.MIN_VALUE));
        assertEquals(0xFF563412, ColorARGB.toABGR(0x78123456, Integer.MAX_VALUE));
        assertEquals(0x80000000, ColorARGB.toABGR(0, 0xFFFFFF80));
        for (int c : EDGE_INTS) {
            for (int alpha = -1024; alpha <= 1024; alpha++) {
                assertEquals(expectedAbgr(c, alpha), ColorARGB.toABGR(c, alpha), Integer.toHexString(c) + " " + alpha);
            }
        }
        SplittableRandom random = new SplittableRandom(0xA1FB);
        for (int i = 0; i < 100_000; i++) {
            int c = random.nextInt(), alpha = random.nextInt();
            assertEquals(expectedAbgr(c, alpha), ColorARGB.toABGR(c, alpha));
            assertEquals(ColorARGB.toABGR(ColorARGB.withAlpha(c, alpha)), ColorARGB.toABGR(c, alpha));
        }
    }

    @Test
    void withAlphaReplacesOnlyTheTopByte() {
        assertEquals(0x9A123456, ColorARGB.withAlpha(0x78123456, 0x9A));
        assertEquals(0x00FFFFFF, ColorARGB.withAlpha(-1, 0));
        assertEquals(0xFF000000, ColorARGB.withAlpha(0, 255));
        assertEquals(0x80000000, ColorARGB.withAlpha(0, 0x80));
        // the GUI's accent tint: an opaque accent comes back at 0xB8
        assertEquals(0xB8E5A50A, ColorARGB.withAlpha(0xFFE5A50A, 0xB8));
        for (int rgb : EDGE_INTS) {
            for (int alpha = 0; alpha <= 255; alpha++) {
                int c = ColorARGB.withAlpha(rgb, alpha);
                assertEquals((rgb & 0x00FFFFFF) | alpha << 24, c);
                assertEquals(alpha, ColorARGB.unpackAlpha(c));
                assertEquals(ColorARGB.unpackRed(rgb), ColorARGB.unpackRed(c));
                assertEquals(ColorARGB.unpackGreen(rgb), ColorARGB.unpackGreen(c));
                assertEquals(ColorARGB.unpackBlue(rgb), ColorARGB.unpackBlue(c));
            }
            assertEquals(rgb, ColorARGB.withAlpha(rgb, ColorARGB.unpackAlpha(rgb)));
        }
    }

    @Test
    void outOfRangeAlphaInWithAlphaKeepsItsLowByteLikePack() {
        assertEquals(0x00123456, ColorARGB.withAlpha(0x78123456, 256));
        assertEquals(0xFF123456, ColorARGB.withAlpha(0x78123456, -1));
        assertEquals(0x34123456, ColorARGB.withAlpha(0x78123456, 0x1234));
        assertEquals(0x00123456, ColorARGB.withAlpha(0x78123456, Integer.MIN_VALUE));
        assertEquals(0xFF123456, ColorARGB.withAlpha(0x78123456, Integer.MAX_VALUE));
        for (int rgb : EDGE_INTS) {
            for (int alpha = -1024; alpha <= 1024; alpha++) {
                assertEquals((rgb & 0x00FFFFFF) | (alpha & 0xFF) << 24, ColorARGB.withAlpha(rgb, alpha));
            }
        }
        SplittableRandom random = new SplittableRandom(0xA1FC);
        for (int i = 0; i < 100_000; i++) {
            int rgb = random.nextInt(), alpha = random.nextInt();
            assertEquals(ColorARGB.pack(ColorARGB.unpackRed(rgb), ColorARGB.unpackGreen(rgb), ColorARGB.unpackBlue(rgb), alpha), ColorARGB.withAlpha(rgb, alpha));
        }
    }

    @Test
    void holderIsFinalWithAPrivateConstructor() throws ReflectiveOperationException {
        assertTrue(Modifier.isFinal(ColorARGB.class.getModifiers()));
        assertTrue(ColorU8.class.isAssignableFrom(ColorARGB.class));
        Constructor<?>[] constructors = ColorARGB.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        // still constructible reflectively, the body does nothing
        constructors[0].setAccessible(true);
        assertNotNull(constructors[0].newInstance());
    }
}
