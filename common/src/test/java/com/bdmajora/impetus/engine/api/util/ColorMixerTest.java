package com.bdmajora.impetus.engine.api.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

class ColorMixerTest {
    // ints that sit on every sign and byte boundary
    private static final int[] EDGE_INTS = {
            0, 1, -1, 0xFF, 0x100, 0xFFFF, 0x10000, 0xFFFFFF, 0x1000000, 0x7F, 0x80, 0x7FFFFFFF, 0x80000000,
            0x80808080, 0x7F7F7F7F, 0x00FF00FF, 0xFF00FF00, 0x12345678, 0x87654321, 0xDEADBEEF, 0xFFFFFF00, 0x00FFFFFF
    };

    // ratios above one that must all act as one
    private static final float[] ABOVE_ONE = {
            Math.nextUp(1f), 1.001f, 1.5f, 2f, 3f, 255f, 256f, 257f, 1e9f, 1e30f, Float.MAX_VALUE, Float.POSITIVE_INFINITY
    };

    // ratios below zero that must all act as zero
    private static final float[] BELOW_ZERO = {
            -Float.MIN_VALUE, -1e-7f, -0.001f, -0.5f, -1f, -2f, -256f, -1e9f, -1e30f, -Float.MAX_VALUE, Float.NEGATIVE_INFINITY
    };

    // one channel of a packed word
    private static int channel(int color, int index) {
        return (color >>> (index * 8)) & 0xFF;
    }

    // reference blend of one channel: a at weight w of 256, rounded half up
    private static int blend(int a, int b, int w) {
        return (a * w + b * (256 - w) + 128) >> 8;
    }

    // reference product of one channel: round(a * b / 255), never a tie because 255 is odd
    private static int product(int a, int b) {
        return (2 * a * b + 255) / 510;
    }

    // reference mix of whole words at an integer weight
    private static int expectedMix(int a, int b, int w) {
        int c = 0;
        for (int i = 0; i < 4; i++) {
            c |= blend(channel(a, i), channel(b, i), w) << (i * 8);
        }
        return c;
    }

    // reference multiply of whole words
    private static int expectedMul(int a, int b) {
        int c = 0;
        for (int i = 0; i < 4; i++) {
            c |= product(channel(a, i), channel(b, i)) << (i * 8);
        }
        return c;
    }

    // reference RGB scale: the factor clamped to 0..255, alpha copied
    private static int expectedMulSingle(int a, int factor) {
        int f = Math.max(0, Math.min(factor, 255));
        int c = a & 0xFF000000;
        for (int i = 0; i < 3; i++) {
            c |= product(channel(a, i), f) << (i * 8);
        }
        return c;
    }

    @Test
    void mixAtEveryWeightMatchesTheRoundedBlendForEveryChannelPair() {
        // every pair lands in every channel, the sign byte included
        for (int w = 0; w <= 256; w++) {
            float ratio = w / 256f;
            for (int a = 0; a <= 255; a++) {
                for (int b = 0; b <= 255; b++) {
                    int aColor = a | b << 8 | a << 16 | b << 24;
                    int bColor = b | a << 8 | b << 16 | a << 24;
                    assertEquals(expectedMix(aColor, bColor, w), ColorMixer.mix(aColor, bColor, ratio));
                }
            }
        }
    }

    @Test
    void mixMatchesTheModelForRandomWordsAndWeights() {
        SplittableRandom random = new SplittableRandom(0x313);
        for (int i = 0; i < 200_000; i++) {
            int a = random.nextInt(), b = random.nextInt(), w = random.nextInt(257);
            assertEquals(expectedMix(a, b, w), ColorMixer.mix(a, b, w / 256f));
        }
        for (int a : EDGE_INTS) {
            for (int b : EDGE_INTS) {
                for (int w = 0; w <= 256; w++) {
                    assertEquals(expectedMix(a, b, w), ColorMixer.mix(a, b, w / 256f));
                }
            }
        }
    }

    @Test
    void mixEndpointsReturnEachColourExactly() {
        assertEquals(0x00FF00FF, ColorMixer.mix(0x00FF00FF, 0x00000000, 1f));
        assertEquals(0x00000000, ColorMixer.mix(0x00FF00FF, 0x00000000, 0f));
        assertEquals(0xFF00FF00, ColorMixer.mix(0xFF00FF00, -1, 1f));
        for (int a : EDGE_INTS) {
            for (int b : EDGE_INTS) {
                assertEquals(a, ColorMixer.mix(a, b, 1f));
                assertEquals(b, ColorMixer.mix(a, b, 0f));
                assertEquals(b, ColorMixer.mix(a, b, -0f));
                // 255.99998 rounds to the full weight
                assertEquals(a, ColorMixer.mix(a, b, Math.nextDown(1f)));
                assertEquals(b, ColorMixer.mix(a, b, Float.MIN_VALUE));
            }
        }
    }

    @Test
    void mixHalfwayRoundsEachChannelUp() {
        assertEquals(0x80808080, ColorMixer.mix(-1, 0, 0.5f));
        assertEquals(0x80808080, ColorMixer.mix(0, -1, 0.5f));
        // 1 and 0 average to 0.5, which rounds up
        assertEquals(0x01010101, ColorMixer.mix(0x01010101, 0, 0.5f));
        for (int a = 0; a <= 255; a++) {
            for (int b = 0; b <= 255; b++) {
                int half = ColorMixer.mix(a * 0x01010101, b * 0x01010101, 0.5f);
                assertEquals(((a + b + 1) >> 1) * 0x01010101, half);
            }
        }
    }

    @Test
    void mixRoundsTheRatioToTheNearestWeight() {
        for (int k = 0; k < 256; k++) {
            float halfway = (k + 0.5f) / 256f;
            for (int a : EDGE_INTS) {
                for (int b : EDGE_INTS) {
                    // a tie between two weights takes the upper one, the float just below it the lower
                    assertEquals(expectedMix(a, b, k + 1), ColorMixer.mix(a, b, halfway));
                    assertEquals(expectedMix(a, b, k + 1), ColorMixer.mix(a, b, Math.nextUp(halfway)));
                    assertEquals(expectedMix(a, b, k), ColorMixer.mix(a, b, Math.nextDown(halfway)));
                }
            }
        }
        // 0.49999997 + 0.5 rounds to 1 in float, so a naive (int) (x + 0.5f) would pick weight 1 here
        assertEquals(0, ColorMixer.mix(-1, 0, Math.nextDown(1f / 512f)));
        assertEquals(0x01010101, ColorMixer.mix(-1, 0, 1f / 512f));
    }

    @Test
    void mixClampsRatiosOutsideZeroToOne() {
        for (int a : EDGE_INTS) {
            for (int b : EDGE_INTS) {
                for (float ratio : ABOVE_ONE) {
                    assertEquals(a, ColorMixer.mix(a, b, ratio), () -> Integer.toHexString(a) + " " + Integer.toHexString(b) + " " + ratio);
                }
                for (float ratio : BELOW_ZERO) {
                    assertEquals(b, ColorMixer.mix(a, b, ratio), () -> Integer.toHexString(a) + " " + Integer.toHexString(b) + " " + ratio);
                }
            }
        }
        // a weight of 512 overflowed every lane into its neighbour
        assertEquals(-1, ColorMixer.mix(-1, 0, 2f));
        assertEquals(0, ColorMixer.mix(-1, 0, -1f));
        SplittableRandom random = new SplittableRandom(0xC1A9);
        for (int i = 0; i < 100_000; i++) {
            int a = random.nextInt(), b = random.nextInt();
            float above = 1f + (float) random.nextDouble(1e-6, 1e6);
            float below = (float) -random.nextDouble(1e-9, 1e6);
            assertEquals(a, ColorMixer.mix(a, b, above));
            assertEquals(b, ColorMixer.mix(a, b, below));
        }
    }

    @Test
    void mixOfNanPicksTheSecondColour() {
        for (int a : EDGE_INTS) {
            for (int b : EDGE_INTS) {
                assertEquals(b, ColorMixer.mix(a, b, Float.NaN));
                assertEquals(b, ColorMixer.mix(a, b, Float.intBitsToFloat(0xFFC00001)));
            }
        }
    }

    @Test
    void mixOfAColourWithItselfIsThatColourAtAnyRatio() {
        float[] specials = {0f, -0f, 0.5f, 1f, Float.NaN, Float.MIN_VALUE, Math.nextDown(1f), 2f, -2f,
                Float.MAX_VALUE, -Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int c : EDGE_INTS) {
            for (float ratio : specials) {
                assertEquals(c, ColorMixer.mix(c, c, ratio));
            }
        }
        SplittableRandom random = new SplittableRandom(0x5E1F);
        for (int i = 0; i < 100_000; i++) {
            int c = random.nextInt();
            assertEquals(c, ColorMixer.mix(c, c, Float.intBitsToFloat(random.nextInt())));
        }
    }

    @Test
    void mixIsSymmetricInItsWeights() {
        SplittableRandom random = new SplittableRandom(0x5733);
        for (int i = 0; i < 20_000; i++) {
            int a = random.nextInt(), b = random.nextInt(), w = random.nextInt(257);
            assertEquals(ColorMixer.mix(a, b, w / 256f), ColorMixer.mix(b, a, (256 - w) / 256f));
        }
        for (int a : EDGE_INTS) {
            for (int b : EDGE_INTS) {
                for (int w = 0; w <= 256; w++) {
                    assertEquals(ColorMixer.mix(a, b, w / 256f), ColorMixer.mix(b, a, (256 - w) / 256f));
                }
            }
        }
    }

    @Test
    void mixStaysWithinOneStepOfTheExactBlendAndBetweenItsEnds() {
        SplittableRandom random = new SplittableRandom(0xB1E4D);
        for (int i = 0; i < 300_000; i++) {
            int a = random.nextInt(), b = random.nextInt();
            float ratio = (float) random.nextDouble();
            int c = ColorMixer.mix(a, b, ratio);
            for (int ch = 0; ch < 4; ch++) {
                int x = channel(a, ch), y = channel(b, ch), z = channel(c, ch);
                double exact = x * (double) ratio + y * (1.0 - ratio);
                // weight rounding costs at most 255 / 512 and the output rounding half a step
                assertTrue(Math.abs(z - exact) < 1.0, () -> x + " " + y + " " + ratio + " -> " + z);
                assertTrue(z >= Math.min(x, y) && z <= Math.max(x, y));
            }
        }
    }

    @Test
    void mixMovesMonotonicallyFromTheSecondColourToTheFirst() {
        int[][] pairs = {{-1, 0}, {0, -1}, {0x12345678, 0x87654321}, {0xFF00FF00, 0x00FF00FF}, {0x80808080, 0x7F7F7F7F}, {0xDEADBEEF, 0x01020304}};
        for (int[] pair : pairs) {
            int previous = ColorMixer.mix(pair[0], pair[1], 0f);
            for (int step = 1; step <= 10_000; step++) {
                int current = ColorMixer.mix(pair[0], pair[1], step / 10_000f);
                for (int ch = 0; ch < 4; ch++) {
                    int direction = Integer.signum(channel(pair[0], ch) - channel(pair[1], ch));
                    assertTrue(direction * (channel(current, ch) - channel(previous, ch)) >= 0);
                }
                previous = current;
            }
            assertEquals(pair[0], previous);
        }
    }

    @Test
    void mulMatchesTheRoundedProductForEveryChannelPair() {
        // every pair lands in every channel, the sign byte included
        for (int x = 0; x <= 255; x++) {
            for (int y = 0; y <= 255; y++) {
                int a = x | y << 8 | x << 16 | y << 24;
                int b = y | x << 8 | y << 16 | x << 24;
                assertEquals(expectedMul(a, b), ColorMixer.mul(a, b));
                int alone = ColorMixer.mul(x * 0x01010101, y * 0x01010101);
                assertEquals(product(x, y) * 0x01010101, alone);
            }
        }
        // 1 * 1 / 255 is 0.004, the old +255 bias made it 1
        assertEquals(0, ColorMixer.mul(0x01010101, 0x01010101));
        assertEquals(0x40404040, ColorMixer.mul(0x80808080, 0x80808080));
        assertEquals(0x80808080, ColorMixer.mul(-1, 0x80808080));
    }

    @Test
    void mulMatchesTheModelForRandomWords() {
        SplittableRandom random = new SplittableRandom(0x3017);
        for (int i = 0; i < 300_000; i++) {
            int a = random.nextInt(), b = random.nextInt();
            assertEquals(expectedMul(a, b), ColorMixer.mul(a, b));
        }
        for (int a : EDGE_INTS) {
            for (int b : EDGE_INTS) {
                assertEquals(expectedMul(a, b), ColorMixer.mul(a, b), () -> Integer.toHexString(a) + " " + Integer.toHexString(b));
            }
        }
    }

    @Test
    void mulByWhiteIsTheIdentityAndByBlackIsBlack() {
        assertEquals(0x12345678, ColorMixer.mul(0x12345678, 0xFFFFFFFF));
        assertEquals(0, ColorMixer.mul(0, 0xFFFFFFFF));
        for (int c : EDGE_INTS) {
            assertEquals(c, ColorMixer.mul(c, -1));
            assertEquals(c, ColorMixer.mul(-1, c));
            assertEquals(0, ColorMixer.mul(c, 0));
            assertEquals(0, ColorMixer.mul(0, c));
        }
        SplittableRandom random = new SplittableRandom(0x1D);
        for (int i = 0; i < 100_000; i++) {
            int c = random.nextInt();
            assertEquals(c, ColorMixer.mul(c, -1));
            assertEquals(0, ColorMixer.mul(0, c));
        }
    }

    @Test
    void mulByAByteMaskKeepsExactlyThoseChannels() {
        SplittableRandom random = new SplittableRandom(0x3A5C);
        for (int bits = 0; bits < 16; bits++) {
            int mask = 0;
            for (int ch = 0; ch < 4; ch++) {
                if ((bits & (1 << ch)) != 0) {
                    mask |= 0xFF << (ch * 8);
                }
            }
            for (int c : EDGE_INTS) {
                assertEquals(c & mask, ColorMixer.mul(c, mask));
                assertEquals(c & mask, ColorMixer.mul(mask, c));
            }
            for (int i = 0; i < 10_000; i++) {
                int c = random.nextInt();
                assertEquals(c & mask, ColorMixer.mul(c, mask));
            }
        }
    }

    @Test
    void mulIsCommutativeAndNeverBrightensAChannel() {
        SplittableRandom random = new SplittableRandom(0xC033);
        for (int i = 0; i < 200_000; i++) {
            int a = random.nextInt(), b = random.nextInt();
            int c = ColorMixer.mul(a, b);
            assertEquals(c, ColorMixer.mul(b, a));
            for (int ch = 0; ch < 4; ch++) {
                assertTrue(channel(c, ch) <= Math.min(channel(a, ch), channel(b, ch)));
            }
        }
    }

    @Test
    void mulSingleMatchesTheRoundedProductForEveryFactorAndChannel() {
        for (int f = 0; f <= 255; f++) {
            for (int v = 0; v <= 255; v++) {
                int a = v | (255 - v) << 8 | (v ^ 0x5A) << 16 | ((v * 7) & 0xFF) << 24;
                assertEquals(expectedMulSingle(a, f), ColorMixer.mulSingleWithoutAlpha(a, f));
                int grey = v * 0x01010101;
                assertEquals((grey & 0xFF000000) | product(v, f) * 0x010101, ColorMixer.mulSingleWithoutAlpha(grey, f));
            }
        }
        assertEquals(0xFF7F7F7F, ColorMixer.mulSingleWithoutAlpha(-1, 127));
        assertEquals(0xFF808080, ColorMixer.mulSingleWithoutAlpha(-1, 128));
        assertEquals(0x00345678, ColorMixer.mulSingleWithoutAlpha(0x00345678, 0xFF));
        assertEquals(0xFF000000, ColorMixer.mulSingleWithoutAlpha(0xFF010101, 1));
    }

    @Test
    void mulSingleMatchesTheModelForRandomWordsAndFactors() {
        SplittableRandom random = new SplittableRandom(0x5146);
        for (int i = 0; i < 300_000; i++) {
            int a = random.nextInt(), f = random.nextInt(256);
            assertEquals(expectedMulSingle(a, f), ColorMixer.mulSingleWithoutAlpha(a, f));
        }
    }

    @Test
    void mulSingleNeverTouchesAlpha() {
        int[] factors = {0, 1, 127, 128, 254, 255, 256, -1, 0x180, Integer.MIN_VALUE, Integer.MAX_VALUE};
        for (int alpha = 0; alpha <= 255; alpha++) {
            for (int rgb : EDGE_INTS) {
                int c = (rgb & 0x00FFFFFF) | alpha << 24;
                for (int f : factors) {
                    assertEquals(alpha, ColorMixer.mulSingleWithoutAlpha(c, f) >>> 24);
                }
            }
        }
    }

    @Test
    void mulSingleClampsOutOfRangeFactors() {
        // the factor was masked, so 256 wrapped to black and -1 to full brightness
        assertEquals(0xFFFFFFFF, ColorMixer.mulSingleWithoutAlpha(-1, 256));
        assertEquals(0xFFFFFFFF, ColorMixer.mulSingleWithoutAlpha(-1, 0x180));
        assertEquals(0xFF000000, ColorMixer.mulSingleWithoutAlpha(-1, -1));
        int[] above = {256, 257, 0x180, 0x1FF, 0x10000, Integer.MAX_VALUE};
        int[] below = {-1, -127, -256, -257, 0xFFFFFF80, Integer.MIN_VALUE};
        for (int c : EDGE_INTS) {
            for (int f : above) {
                assertEquals(c, ColorMixer.mulSingleWithoutAlpha(c, f));
            }
            for (int f : below) {
                assertEquals(c & 0xFF000000, ColorMixer.mulSingleWithoutAlpha(c, f));
            }
        }
        SplittableRandom random = new SplittableRandom(0xFAC7);
        for (int i = 0; i < 100_000; i++) {
            int c = random.nextInt(), f = random.nextInt();
            assertEquals(expectedMulSingle(c, f), ColorMixer.mulSingleWithoutAlpha(c, f));
        }
    }

    @Test
    void mulSingleAgreesWithMulByAnOpaqueGrey() {
        SplittableRandom random = new SplittableRandom(0x6E7);
        for (int f = 0; f <= 255; f++) {
            int grey = 0xFF000000 | f * 0x010101;
            for (int c : EDGE_INTS) {
                assertEquals(ColorMixer.mul(c, grey), ColorMixer.mulSingleWithoutAlpha(c, f));
            }
            for (int i = 0; i < 1_000; i++) {
                int c = random.nextInt();
                assertEquals(ColorMixer.mul(c, grey), ColorMixer.mulSingleWithoutAlpha(c, f));
            }
        }
    }

    @Test
    void holderIsFinalWithAPrivateConstructor() throws ReflectiveOperationException {
        assertTrue(Modifier.isFinal(ColorMixer.class.getModifiers()));
        Constructor<?>[] constructors = ColorMixer.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        // still constructible reflectively, the body does nothing
        constructors[0].setAccessible(true);
        assertNotNull(constructors[0].newInstance());
    }
}
