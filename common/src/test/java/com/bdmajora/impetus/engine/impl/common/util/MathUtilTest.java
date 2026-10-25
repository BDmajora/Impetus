package com.bdmajora.impetus.engine.impl.common.util;

import com.bdmajora.testing.FreshClasses;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.function.LongPredicate;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;

class MathUtilTest {
    private static final int MIN = Integer.MIN_VALUE;
    private static final int MAX = Integer.MAX_VALUE;

    // ints on the sign, power-of-two and overflow boundaries, plus consecutive Fibonacci numbers (Euclid's worst case)
    private static final int[] EDGE_INTS = {
            0, 1, -1, 2, -2, 3, -3, 6, -6, 7, 8, -8, 9, 12, 16, 18, 20, 28, 32, 255, 256, 46340, 46341, 46342, 65535, 65536,
            65537, 1 << 20, 1 << 29, 1 << 30, -(1 << 30), (1 << 30) + 1, 1134903170, 1836311903, MAX - 1, MAX, MIN + 1, MIN
    };

    // floats on the zero, unit, tolerance, integer-precision and int-range boundaries, plus every kind of special
    private static final float[] EDGE_FLOATS = {
            0f, -0f, 1f, -1f, 0.5f, -0.5f, 1.5f, -1.5f, Math.nextDown(1f), Math.nextUp(1f), Math.nextUp(-1f), Math.nextDown(-1f),
            Float.MIN_VALUE, -Float.MIN_VALUE, Float.MIN_NORMAL, 1e-5f, -1e-5f, Math.nextDown(1e-5f), 1e-7f, 0x1p23f, 0x1p23f + 1,
            0x1p30f, 2147483520f, 0x1p31f, -0x1p31f, Math.nextDown(-0x1p31f), 0x1p63f, 0x1p64f, 1e7f, Float.MAX_VALUE,
            -Float.MAX_VALUE, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN, Float.intBitsToFloat(0x7FC00001),
            Float.intBitsToFloat(0xFFFFFFFF)
    };

    // doubles around zero, the unit, the world border, the int range and the integer-precision limit
    private static final double[] EDGE_DOUBLES = {
            0.0, -0.0, 1.0, -1.0, 1.9, -1.5, Math.nextDown(1.0), Math.nextDown(-1.0), Math.nextUp(-1.0), Double.MIN_VALUE,
            -Double.MIN_VALUE, 30_000_000.5, -30_000_000.5, 2147483647.0, 2147483647.5, 2147483648.0, -2147483647.5,
            -2147483648.0, -2147483648.5, -2147483649.0, 0x1p52, -0x1p52, 0x1p53 + 2, 1e300, -1e300, Double.MAX_VALUE,
            -Double.MAX_VALUE, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN
    };

    // one uninstrumented copy of MathUtil and the sweeps: FreshClasses defines them without a code source, so JaCoCo's shared probe writes do not serialise the threads
    private static final FreshClasses UNINSTRUMENTED = new FreshClasses(MathUtil.class, Sweeps.class);

    // runs one sweep over all 2^32 patterns on the uninstrumented copy and returns its first failing pattern, or -1
    private static long sweep(String name) throws ReflectiveOperationException {
        Class<?> copy = UNINSTRUMENTED.load(MathUtil.class);
        assertNotSame(MathUtil.class, copy);
        assertTrue(Arrays.stream(copy.getDeclaredMethods()).noneMatch(Method::isSynthetic), "the copy was instrumented");
        return (long) UNINSTRUMENTED.load(Sweeps.class).getMethod(name).invoke(null);
    }

    private static void assertSweepClean(String name) throws ReflectiveOperationException {
        long bad = sweep(name);
        assertEquals(-1, bad, () -> name + " failed at 0x" + Long.toHexString(bad) + " (" + Float.intBitsToFloat((int) bad) + ")");
    }

    private static int bits(float f) {
        return Float.floatToRawIntBits(f);
    }

    // the exhaustive checks; public so the fresh copy is reachable reflectively, and nothing here touches the outer class
    public static final class Sweeps {
        // first pattern of all 2^32 that fails, scanning 65536-pattern blocks in parallel, or -1
        private static long first(LongPredicate fails) {
            return LongStream.rangeClosed(0, 0xFFFF).parallel()
                    .map(block -> {
                        for (long bits = block << 16, end = bits | 0xFFFF; bits <= end; bits++) {
                            if (fails.test(bits)) {
                                return bits;
                            }
                        }
                        return -1;
                    })
                    .filter(bits -> bits >= 0)
                    .findFirst()
                    .orElse(-1);
        }

        // exactly the positive ints with one bit set
        public static long powerOfTwoMismatch() {
            return first(bits -> {
                int n = (int) bits;
                return MathUtil.isPowerOfTwo(n) != (n > 0 && Integer.bitCount(n) == 1);
            });
        }

        // r <= f < r + 1 inside the int range, saturation outside it, 0 for NaN, and the double overload agrees on every float
        public static long floorMismatch() {
            return first(bits -> {
                float f = Float.intBitsToFloat((int) bits);
                int r = MathUtil.mojfloor(f);
                if (r != MathUtil.mojfloor((double) f)) {
                    return true;
                }
                if (f != f) {
                    return r != 0;
                }
                if (f >= 0x1p31f) {
                    return r != Integer.MAX_VALUE;
                }
                if (f < -0x1p31f) {
                    return r != Integer.MIN_VALUE;
                }
                return !(r <= (double) f && (double) f < r + 1.0);
            });
        }

        // vanilla MathHelper.floor's cast-and-step everywhere it does not wrap
        public static long vanillaFloorMismatch() {
            return first(bits -> {
                float f = Float.intBitsToFloat((int) bits);
                if (f < -0x1p31f) {
                    return false;
                }
                int truncated = (int) f;
                return MathUtil.mojfloor(f) != (f < (float) truncated ? truncated - 1 : truncated);
            });
        }

        // NaN and everything up to zero (including -0) is +0, everything from one up is 1, and the rest passes through bit-exact
        public static long saturateMismatch() {
            return first(bits -> {
                float f = Float.intBitsToFloat((int) bits);
                float expected = f != f || f <= 0f ? 0f : (f >= 1f ? 1f : f);
                int got = Float.floatToRawIntBits(MathUtil.saturate(f));
                return got != Float.floatToRawIntBits(expected) || (f == f && got != Float.floatToRawIntBits(MathUtil.clamp(f, 0f, 1f)));
            });
        }

        // a float product of two floats is one rounding of the exact product, which a double holds without loss
        public static long squareMismatch() {
            return first(bits -> {
                float f = Float.intBitsToFloat((int) bits);
                return Float.floatToIntBits(MathUtil.square(f)) != Float.floatToIntBits((float) ((double) f * f));
            });
        }

        // each float against itself and its upward neighbour, whose gap float subtraction gets exactly, so a double model can judge it
        public static long roughlyEqualMismatch() {
            return first(bits -> {
                float f = Float.intBitsToFloat((int) bits);
                float up = Math.nextUp(f);
                boolean neighbours = f == up || Math.abs((double) up - f) < 1e-5f;
                return MathUtil.roughlyEqual(f, f) != (f == f) || MathUtil.roughlyEqual(f, up) != neighbours || MathUtil.roughlyEqual(up, f) != neighbours;
            });
        }
    }

    @Test
    void utilityClassIsFinalWithAPrivateConstructorAndOnlyStaticMembers() throws ReflectiveOperationException {
        assertTrue(Modifier.isFinal(MathUtil.class.getModifiers()));
        Constructor<?>[] constructors = MathUtil.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        assertEquals(0, constructors[0].getParameterCount());
        constructors[0].setAccessible(true);
        assertNotNull(constructors[0].newInstance());
        // JaCoCo adds synthetic members to the loaded class; only the declared ones count
        List<Method> methods = Arrays.stream(MathUtil.class.getDeclaredMethods()).filter(m -> !m.isSynthetic()).toList();
        assertEquals(List.of("clamp", "clamp", "gcd", "isPowerOfTwo", "lcm", "mojfloor", "mojfloor", "nearestToZero", "roughlyEqual",
                        "roundToward", "saturate", "square", "toMib"),
                methods.stream().filter(m -> Modifier.isPublic(m.getModifiers())).map(Method::getName).sorted().toList());
        assertEquals(List.of("unsignedGcd"), methods.stream().filter(m -> Modifier.isPrivate(m.getModifiers())).map(Method::getName).toList());
        for (Method method : methods) {
            assertTrue(Modifier.isStatic(method.getModifiers()), method.getName());
        }
        assertTrue(Arrays.stream(MathUtil.class.getDeclaredFields()).allMatch(Field::isSynthetic));
    }

    @Test
    void classHasNoStaticInitialiser() throws IOException {
        ClassModel model;
        try (InputStream in = Objects.requireNonNull(MathUtil.class.getResourceAsStream("MathUtil.class"))) {
            model = ClassFile.of().parse(in.readAllBytes());
        }
        assertTrue(model.methods().stream().map(MethodModel::methodName).noneMatch(name -> name.equalsString("<clinit>")));
    }

    @Test
    void powerOfTwoAcceptsExactlyTheThirtyOnePositivePowers() {
        for (int k = 0; k < 31; k++) {
            assertTrue(MathUtil.isPowerOfTwo(1 << k), "2^" + k);
            assertFalse(MathUtil.isPowerOfTwo(-(1 << k)), "-2^" + k);
            if (k >= 1) {
                assertFalse(MathUtil.isPowerOfTwo((1 << k) + 1), "2^" + k + " + 1");
            }
            if (k >= 2) {
                assertFalse(MathUtil.isPowerOfTwo((1 << k) - 1), "2^" + k + " - 1");
            }
        }
        // 1 << 31 has one bit set but is MIN_VALUE
        assertFalse(MathUtil.isPowerOfTwo(1 << 31));
        assertFalse(MathUtil.isPowerOfTwo(0));
        assertFalse(MathUtil.isPowerOfTwo(-1));
        assertFalse(MathUtil.isPowerOfTwo(MAX));
        assertFalse(MathUtil.isPowerOfTwo(12));
    }

    @Test
    void powerOfTwoSweepOverEveryInt() throws ReflectiveOperationException {
        assertSweepClean("powerOfTwoMismatch");
    }

    @Test
    void gcdOfKnownPairs() {
        assertEquals(6, MathUtil.gcd(12, 18));
        assertEquals(6, MathUtil.gcd(18, 12));
        assertEquals(1, MathUtil.gcd(17, 5));
        assertEquals(4, MathUtil.gcd(20, 32));
        assertEquals(7, MathUtil.gcd(7, 7));
        assertEquals(1, MathUtil.gcd(1, MAX));
        assertEquals(MAX, MathUtil.gcd(MAX, MAX));
        assertEquals(1, MathUtil.gcd(MAX, MAX - 1));
        assertEquals(1 << 20, MathUtil.gcd(1 << 30, 1 << 20));
        // 2^12 * 9 and 2^10 * 15 share 2^10 and 3
        assertEquals(3 << 10, MathUtil.gcd(9 << 12, 15 << 10));
        assertEquals(1, MathUtil.gcd(1836311903, 1134903170));
    }

    @Test
    void gcdWithZeroIsTheOtherMagnitudeAndBothZeroIsZero() {
        assertEquals(0, MathUtil.gcd(0, 0));
        assertEquals(7, MathUtil.gcd(0, 7));
        assertEquals(7, MathUtil.gcd(7, 0));
        assertEquals(7, MathUtil.gcd(0, -7));
        assertEquals(7, MathUtil.gcd(-7, 0));
        assertEquals(MAX, MathUtil.gcd(0, MAX));
        assertEquals(MAX, MathUtil.gcd(MIN + 1, 0));
    }

    @Test
    void gcdIsNonNegativeWhateverTheSigns() {
        assertEquals(2, MathUtil.gcd(-4, 6));
        assertEquals(2, MathUtil.gcd(4, -6));
        assertEquals(2, MathUtil.gcd(-4, -6));
        assertEquals(1, MathUtil.gcd(-1, -1));
        assertEquals(MAX, MathUtil.gcd(MIN + 1, MIN + 1));
    }

    @Test
    void gcdReadsMinValueAsTwoToTheThirtyOne() {
        assertEquals(2, MathUtil.gcd(MIN, 6));
        assertEquals(2, MathUtil.gcd(6, MIN));
        assertEquals(1, MathUtil.gcd(MIN, 3));
        assertEquals(1, MathUtil.gcd(MIN, MAX));
        assertEquals(1, MathUtil.gcd(MIN, MIN + 1));
        assertEquals(1 << 30, MathUtil.gcd(MIN, 1 << 30));
        assertEquals(1 << 30, MathUtil.gcd(MIN, -(1 << 30)));
        assertEquals(8, MathUtil.gcd(MIN, -8));
    }

    @Test
    void gcdThatWouldBeTwoToTheThirtyOneThrows() {
        for (int[] pair : new int[][]{{MIN, 0}, {0, MIN}, {MIN, MIN}}) {
            ArithmeticException e = assertThrows(ArithmeticException.class, () -> MathUtil.gcd(pair[0], pair[1]));
            assertEquals("gcd(" + pair[0] + ", " + pair[1] + ") is 2^31, which overflows int", e.getMessage());
        }
    }

    @Test
    void gcdMatchesBigIntegerOnEdgesSmallValuesAndRandomPairs() {
        for (int a : EDGE_INTS) {
            for (int b : EDGE_INTS) {
                checkGcd(a, b);
            }
        }
        for (int a = -64; a <= 64; a++) {
            for (int b = -64; b <= 64; b++) {
                checkGcd(a, b);
            }
        }
        SplittableRandom random = new SplittableRandom(0x6CD);
        for (int i = 0; i < 100_000; i++) {
            checkGcd(random.nextInt(), random.nextInt());
            // a shared factor with a few trailing zeros, so the result is rarely 1
            int factor = random.nextInt(1, 1 << 12) << random.nextInt(8);
            checkGcd(factor * random.nextInt(-1 << 12, 1 << 12), factor * random.nextInt(-1 << 12, 1 << 12));
            checkGcd(random.nextInt() >> random.nextInt(32), random.nextInt() >> random.nextInt(32));
        }
    }

    // BigInteger's gcd is the non-negative gcd of the magnitudes; 2^31 has to throw
    private static void checkGcd(int a, int b) {
        BigInteger expected = BigInteger.valueOf(a).gcd(BigInteger.valueOf(b));
        if (expected.bitLength() > 31) {
            assertThrows(ArithmeticException.class, () -> MathUtil.gcd(a, b));
        } else {
            assertEquals(expected.intValueExact(), MathUtil.gcd(a, b), () -> "gcd(" + a + ", " + b + ")");
        }
    }

    @Test
    void lcmOfKnownPairs() {
        assertEquals(12, MathUtil.lcm(4, 6));
        assertEquals(12, MathUtil.lcm(6, 4));
        assertEquals(1, MathUtil.lcm(1, 1));
        assertEquals(7, MathUtil.lcm(7, 7));
        assertEquals(160, MathUtil.lcm(20, 32));
        assertEquals(MAX, MathUtil.lcm(MAX, 1));
        assertEquals(MAX, MathUtil.lcm(MAX, MAX));
        assertEquals(1 << 30, MathUtil.lcm(1 << 30, 1 << 29));
        // consecutive, so coprime: 46340 * 46341 still fits in an int and 46341 * 46342 does not
        assertEquals(46340 * 46341, MathUtil.lcm(46340, 46341));
    }

    @Test
    void lcmWithZeroIsZero() {
        assertEquals(0, MathUtil.lcm(0, 0));
        assertEquals(0, MathUtil.lcm(0, 5));
        assertEquals(0, MathUtil.lcm(5, 0));
        assertEquals(0, MathUtil.lcm(MIN, 0));
        assertEquals(0, MathUtil.lcm(0, MIN));
    }

    @Test
    void lcmIsNonNegativeWhateverTheSigns() {
        assertEquals(12, MathUtil.lcm(-4, 6));
        assertEquals(12, MathUtil.lcm(4, -6));
        assertEquals(12, MathUtil.lcm(-4, -6));
        assertEquals(1, MathUtil.lcm(-1, -1));
        assertEquals(MAX, MathUtil.lcm(MIN + 1, -1));
    }

    @Test
    void lcmPastMaxValueThrowsInsteadOfWrapping() {
        int[][] pairs = {{46341, 46342}, {-46341, 46342}, {65536, 65537}, {MAX, MAX - 1}, {1 << 30, 3}, {MIN, 1}, {1, MIN},
                {MIN, -1}, {MIN, MIN}, {MIN, MAX}};
        for (int[] pair : pairs) {
            ArithmeticException e = assertThrows(ArithmeticException.class, () -> MathUtil.lcm(pair[0], pair[1]));
            assertEquals("lcm(" + pair[0] + ", " + pair[1] + ") overflows int", e.getMessage());
        }
    }

    @Test
    void lcmReducesVertexStridesLikeTheRegionManager() {
        assertEquals(1, IntStream.empty().reduce(1, MathUtil::lcm));
        assertEquals(20, IntStream.of(20).reduce(1, MathUtil::lcm));
        assertEquals(160, IntStream.of(20, 32, 20).reduce(1, MathUtil::lcm));
        assertEquals(420, IntStream.of(12, 20, 28).reduce(1, MathUtil::lcm));
    }

    @Test
    void lcmMatchesBigIntegerOnEdgesSmallValuesAndRandomPairs() {
        for (int a : EDGE_INTS) {
            for (int b : EDGE_INTS) {
                checkLcm(a, b);
            }
        }
        for (int a = -64; a <= 64; a++) {
            for (int b = -64; b <= 64; b++) {
                checkLcm(a, b);
            }
        }
        SplittableRandom random = new SplittableRandom(0x1C3);
        for (int i = 0; i < 100_000; i++) {
            checkLcm(random.nextInt(), random.nextInt());
            checkLcm(random.nextInt(-1 << 16, 1 << 16), random.nextInt(-1 << 16, 1 << 16));
            int factor = random.nextInt(1, 1 << 12) << random.nextInt(8);
            checkLcm(factor * random.nextInt(-1 << 12, 1 << 12), factor * random.nextInt(-1 << 12, 1 << 12));
        }
    }

    // |a * b| / gcd in BigInteger, 0 when either is 0; anything past MAX_VALUE has to throw
    private static void checkLcm(int a, int b) {
        BigInteger x = BigInteger.valueOf(a);
        BigInteger y = BigInteger.valueOf(b);
        BigInteger expected = a == 0 || b == 0 ? BigInteger.ZERO : x.multiply(y).abs().divide(x.gcd(y));
        if (expected.bitLength() > 31) {
            assertThrows(ArithmeticException.class, () -> MathUtil.lcm(a, b), () -> "lcm(" + a + ", " + b + ")");
        } else {
            assertEquals(expected.intValueExact(), MathUtil.lcm(a, b), () -> "lcm(" + a + ", " + b + ")");
        }
    }

    @Test
    void roundTowardRoundsUpToTheNextMultiple() {
        assertEquals(16, MathUtil.roundToward(9, 8));
        assertEquals(8, MathUtil.roundToward(8, 8));
        assertEquals(8, MathUtil.roundToward(1, 8));
        assertEquals(0, MathUtil.roundToward(0, 8));
        assertEquals(0, MathUtil.roundToward(-1, 8));
        assertEquals(-8, MathUtil.roundToward(-8, 8));
        assertEquals(-8, MathUtil.roundToward(-9, 8));
        assertEquals(-15, MathUtil.roundToward(-17, 5));
        assertEquals(7, MathUtil.roundToward(7, 1));
        assertEquals(MAX, MathUtil.roundToward(1, MAX));
        assertEquals(MAX, MathUtil.roundToward(MAX, MAX));
        assertEquals(0, MathUtil.roundToward(-1, MAX));
        assertEquals(2147483640, MathUtil.roundToward(2147483633, 8));
        assertEquals(MAX, MathUtil.roundToward(MAX, 1));
    }

    @Test
    void roundTowardIsExactAtMinValueForEveryFactor() {
        assertEquals(MIN, MathUtil.roundToward(MIN, 1));
        assertEquals(MIN, MathUtil.roundToward(MIN, 8));
        assertEquals(MIN, MathUtil.roundToward(MIN, 1 << 30));
        // -MIN_VALUE wraps, so negating before the divide gets these wrong
        assertEquals(-2147483646, MathUtil.roundToward(MIN, 3));
        assertEquals(-2147483640, MathUtil.roundToward(MIN, 10));
        assertEquals(-MAX, MathUtil.roundToward(MIN, MAX));
        assertEquals(-2147483646, MathUtil.roundToward(MIN + 1, 2));
    }

    @Test
    void roundTowardPastMaxValueThrowsInsteadOfWrapping() {
        int[][] cases = {{MAX, 2}, {2147483641, 8}, {MAX - 1, 1 << 30}, {(1 << 30) + 1, 1 << 30}, {MAX, 1 << 30}};
        for (int[] c : cases) {
            ArithmeticException e = assertThrows(ArithmeticException.class, () -> MathUtil.roundToward(c[0], c[1]));
            assertEquals("roundToward(" + c[0] + ", " + c[1] + ") overflows int", e.getMessage());
        }
    }

    @Test
    void roundTowardRefusesAFactorBelowOne() {
        for (int factor : new int[]{0, -1, -8, MIN}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> MathUtil.roundToward(9, factor));
            assertEquals("Factor must be positive, got " + factor, e.getMessage());
        }
    }

    @Test
    void roundTowardMatchesALongModel() {
        for (int value : EDGE_INTS) {
            for (int factor : EDGE_INTS) {
                if (factor > 0) {
                    checkRound(value, factor);
                }
            }
        }
        for (int value = -100; value <= 100; value++) {
            for (int factor = 1; factor <= 40; factor++) {
                checkRound(value, factor);
            }
        }
        SplittableRandom random = new SplittableRandom(0x40D);
        for (int i = 0; i < 100_000; i++) {
            checkRound(random.nextInt(), random.nextInt(1, MAX));
            checkRound(random.nextInt(), random.nextInt(1, 1 << 16));
            checkRound(random.nextInt(-1 << 20, 1 << 20), random.nextInt(1, 64));
        }
    }

    // the ceiling quotient by negated floor division in long, where nothing wraps
    private static void checkRound(int value, int factor) {
        long expected = -Math.floorDiv(-(long) value, factor) * factor;
        if (expected > MAX) {
            assertThrows(ArithmeticException.class, () -> MathUtil.roundToward(value, factor), () -> "roundToward(" + value + ", " + factor + ")");
            return;
        }
        int rounded = MathUtil.roundToward(value, factor);
        assertEquals(expected, rounded, () -> "roundToward(" + value + ", " + factor + ")");
        assertEquals(0, rounded % factor);
        assertTrue(rounded >= value && (long) rounded - factor < value);
    }

    @Test
    void toMibTruncatesTowardZero() {
        assertEquals(0, MathUtil.toMib(0));
        assertEquals(0, MathUtil.toMib(1));
        assertEquals(0, MathUtil.toMib((1L << 20) - 1));
        assertEquals(1, MathUtil.toMib(1L << 20));
        assertEquals(1, MathUtil.toMib((1L << 20) + 1));
        assertEquals(3, MathUtil.toMib(3L << 20));
        assertEquals(4, MathUtil.toMib((5L << 20) - 1));
        assertEquals(0, MathUtil.toMib(-1));
        assertEquals(0, MathUtil.toMib(-(1L << 20) + 1));
        assertEquals(-1, MathUtil.toMib(-(1L << 20)));
        assertEquals(-1, MathUtil.toMib(-(1L << 20) - 1));
        assertEquals((1L << 43) - 1, MathUtil.toMib(Long.MAX_VALUE));
        assertEquals(-(1L << 43), MathUtil.toMib(Long.MIN_VALUE));
        // a negative size reads as the negated positive one, never a MiB further from zero
        for (long bytes : new long[]{1, (1L << 20) - 1, 1L << 20, (3L << 20) + 5, Long.MAX_VALUE}) {
            assertEquals(-MathUtil.toMib(bytes), MathUtil.toMib(-bytes));
        }
        SplittableRandom random = new SplittableRandom(0x313);
        BigInteger mib = BigInteger.ONE.shiftLeft(20);
        for (int i = 0; i < 10_000; i++) {
            long bytes = random.nextLong();
            assertEquals(BigInteger.valueOf(bytes).divide(mib).longValueExact(), MathUtil.toMib(bytes));
        }
    }

    @Test
    void squareIsTheCorrectlyRoundedFloatProduct() {
        assertEquals(9f, MathUtil.square(3f));
        assertEquals(9f, MathUtil.square(-3f));
        assertEquals(0.25f, MathUtil.square(0.5f));
        assertEquals(256f, MathUtil.square(16f));
        assertEquals(bits(0f), bits(MathUtil.square(-0f)));
        assertEquals(0x1p126f, MathUtil.square(0x1p63f));
        assertEquals(Float.POSITIVE_INFINITY, MathUtil.square(0x1p64f));
        assertEquals(Float.POSITIVE_INFINITY, MathUtil.square(Float.MAX_VALUE));
        assertEquals(Float.POSITIVE_INFINITY, MathUtil.square(Float.NEGATIVE_INFINITY));
        assertTrue(Float.isNaN(MathUtil.square(Float.NaN)));
        assertEquals(2 * Float.MIN_VALUE, MathUtil.square(0x1p-74f));
        // 2^-150 is exactly half the smallest subnormal and the tie goes to even, which is zero
        assertEquals(bits(0f), bits(MathUtil.square(0x1p-75f)));
        assertEquals(bits(0f), bits(MathUtil.square(-Float.MIN_VALUE)));
    }

    @Test
    void squareSweepOverEveryFloat() throws ReflectiveOperationException {
        assertSweepClean("squareMismatch");
    }

    @Test
    void floatFloorRoundsTowardNegativeInfinity() {
        assertEquals(1, MathUtil.mojfloor(1.5f));
        assertEquals(-2, MathUtil.mojfloor(-1.5f));
        assertEquals(1, MathUtil.mojfloor(1f));
        assertEquals(-1, MathUtil.mojfloor(-1f));
        assertEquals(0, MathUtil.mojfloor(0f));
        assertEquals(0, MathUtil.mojfloor(-0f));
        assertEquals(0, MathUtil.mojfloor(Math.nextDown(1f)));
        assertEquals(-1, MathUtil.mojfloor(Math.nextUp(-1f)));
        assertEquals(-2, MathUtil.mojfloor(Math.nextDown(-1f)));
        assertEquals(0, MathUtil.mojfloor(Float.MIN_VALUE));
        assertEquals(-1, MathUtil.mojfloor(-Float.MIN_VALUE));
        assertEquals(8388609, MathUtil.mojfloor(0x1p23f + 1));
        assertEquals(-8388609, MathUtil.mojfloor(-0x1p23f - 1));
        assertEquals(1 << 30, MathUtil.mojfloor(0x1p30f));
        assertEquals(2147483520, MathUtil.mojfloor(2147483520f));
        assertEquals(MIN, MathUtil.mojfloor(-0x1p31f));
        // the culler's section radius: search distance over 16
        assertEquals(12, MathUtil.mojfloor(200f / 16f));
    }

    @Test
    void floatFloorSaturatesOutsideTheIntRangeAndReadsNaNAsZero() {
        assertEquals(MAX, MathUtil.mojfloor(0x1p31f));
        assertEquals(MAX, MathUtil.mojfloor(Float.MAX_VALUE));
        assertEquals(MAX, MathUtil.mojfloor(Float.POSITIVE_INFINITY));
        // the cast saturates at MIN_VALUE, where stepping down for the fraction would wrap to MAX_VALUE
        assertEquals(MIN, MathUtil.mojfloor(Math.nextDown(-0x1p31f)));
        assertEquals(MIN, MathUtil.mojfloor(-Float.MAX_VALUE));
        assertEquals(MIN, MathUtil.mojfloor(Float.NEGATIVE_INFINITY));
        assertEquals(0, MathUtil.mojfloor(Float.NaN));
        assertEquals(0, MathUtil.mojfloor(Float.intBitsToFloat(0xFFFFFFFF)));
    }

    @Test
    void floatFloorSweepOverEveryFloat() throws ReflectiveOperationException {
        assertSweepClean("floorMismatch");
    }

    @Test
    void floatFloorSweepAgreesWithVanillaWhereVanillaDoesNotWrap() throws ReflectiveOperationException {
        assertSweepClean("vanillaFloorMismatch");
    }

    @Test
    void doubleFloorRoundsTowardNegativeInfinity() {
        assertEquals(1, MathUtil.mojfloor(1.9));
        assertEquals(-2, MathUtil.mojfloor(-1.5));
        assertEquals(-1, MathUtil.mojfloor(-1.0));
        assertEquals(0, MathUtil.mojfloor(-0.0));
        assertEquals(0, MathUtil.mojfloor(Math.nextDown(1.0)));
        assertEquals(-1, MathUtil.mojfloor(Math.nextUp(-1.0)));
        assertEquals(-2, MathUtil.mojfloor(Math.nextDown(-1.0)));
        assertEquals(0, MathUtil.mojfloor(Double.MIN_VALUE));
        assertEquals(-1, MathUtil.mojfloor(-Double.MIN_VALUE));
        assertEquals(30_000_000, MathUtil.mojfloor(30_000_000.5));
        assertEquals(-30_000_001, MathUtil.mojfloor(-30_000_000.5));
        assertEquals(MAX, MathUtil.mojfloor(2147483647.0));
        assertEquals(MAX, MathUtil.mojfloor(2147483647.5));
        assertEquals(MIN, MathUtil.mojfloor(-2147483647.5));
        assertEquals(MIN, MathUtil.mojfloor(-2147483648.0));
    }

    @Test
    void doubleFloorSaturatesOutsideTheIntRangeAndReadsNaNAsZero() {
        assertEquals(MAX, MathUtil.mojfloor(2147483648.0));
        assertEquals(MAX, MathUtil.mojfloor(0x1p52));
        assertEquals(MAX, MathUtil.mojfloor(Double.MAX_VALUE));
        assertEquals(MAX, MathUtil.mojfloor(Double.POSITIVE_INFINITY));
        // the cast saturates at MIN_VALUE, where stepping down for the fraction would wrap to MAX_VALUE
        assertEquals(MIN, MathUtil.mojfloor(-2147483648.5));
        assertEquals(MIN, MathUtil.mojfloor(-2147483649.0));
        assertEquals(MIN, MathUtil.mojfloor(-0x1p52));
        assertEquals(MIN, MathUtil.mojfloor(-Double.MAX_VALUE));
        assertEquals(MIN, MathUtil.mojfloor(Double.NEGATIVE_INFINITY));
        assertEquals(0, MathUtil.mojfloor(Double.NaN));
        assertEquals(0, MathUtil.mojfloor(Double.longBitsToDouble(0xFFFFFFFFFFFFFFFFL)));
    }

    @Test
    void doubleFloorHoldsTheFloorPropertyOnEdgesAndRandomDoubles() {
        for (double d : EDGE_DOUBLES) {
            checkFloor(d);
        }
        SplittableRandom random = new SplittableRandom(0xF100);
        for (int i = 0; i < 200_000; i++) {
            checkFloor(Double.longBitsToDouble(random.nextLong()));
            double whole = random.nextInt();
            checkFloor(whole);
            checkFloor(Math.nextDown(whole));
            checkFloor(Math.nextUp(whole));
            checkFloor(random.nextDouble(-0x1p32, 0x1p32));
            checkFloor(random.nextDouble(-2.0, 2.0));
        }
    }

    // r <= d < r + 1 in range, saturation outside it, 0 for NaN, and vanilla's cast-and-step wherever that does not wrap
    private static void checkFloor(double d) {
        int r = MathUtil.mojfloor(d);
        if (d != d) {
            assertEquals(0, r);
        } else if (d >= 0x1p31) {
            assertEquals(MAX, r, () -> Double.toString(d));
        } else if (d < -0x1p31) {
            assertEquals(MIN, r, () -> Double.toString(d));
        } else {
            assertTrue(r <= d && d < r + 1.0, () -> "mojfloor(" + d + ") = " + r);
        }
        if (!(d < -0x1p31)) {
            int truncated = (int) d;
            assertEquals(d < truncated ? truncated - 1 : truncated, r, () -> Double.toString(d));
        }
    }

    @Test
    void intClampPinsToTheNearerBound() {
        assertEquals(5, MathUtil.clamp(9, 0, 5));
        assertEquals(0, MathUtil.clamp(-9, 0, 5));
        assertEquals(3, MathUtil.clamp(3, 0, 5));
        assertEquals(0, MathUtil.clamp(0, 0, 5));
        assertEquals(5, MathUtil.clamp(5, 0, 5));
        assertEquals(0, MathUtil.clamp(MIN, 0, 5));
        assertEquals(5, MathUtil.clamp(MAX, 0, 5));
        assertEquals(MIN, MathUtil.clamp(MIN, MIN, MAX));
        assertEquals(MAX, MathUtil.clamp(MAX, MIN, MAX));
        assertEquals(4, MathUtil.clamp(7, 4, 4));
        assertEquals(4, MathUtil.clamp(1, 4, 4));
        assertEquals(MAX, MathUtil.clamp(MIN, MAX, MAX));
        assertEquals(MIN, MathUtil.clamp(MAX, MIN, MIN));
        // the light and thread-priority ranges callers pass
        assertEquals(8, MathUtil.clamp(0, 8, 248));
        assertEquals(248, MathUtil.clamp(255, 8, 248));
        assertEquals(Thread.MAX_PRIORITY, MathUtil.clamp(42, Thread.MIN_PRIORITY, Thread.MAX_PRIORITY));
    }

    @Test
    void intClampWithInvertedBoundsReturnsMax() {
        // a scroll bar whose content is narrower than its view passes a negative max
        assertEquals(-20, MathUtil.clamp(0, 0, -20));
        assertEquals(-20, MathUtil.clamp(100, 0, -20));
        assertEquals(-20, MathUtil.clamp(-100, 0, -20));
        assertEquals(MIN, MathUtil.clamp(5, MAX, MIN));
    }

    @Test
    void intClampMatchesTheJdkClampOnEveryOrderedEdgeTriple() {
        for (int value : EDGE_INTS) {
            for (int min : EDGE_INTS) {
                for (int max : EDGE_INTS) {
                    int expected = min <= max ? Math.clamp(value, min, max) : max;
                    assertEquals(expected, MathUtil.clamp(value, min, max), () -> "clamp(" + value + ", " + min + ", " + max + ")");
                }
            }
        }
    }

    @Test
    void floatClampFollowsMathMinMaxOrdering() {
        assertEquals(2.5f, MathUtil.clamp(2.5f, 0f, 5f));
        assertEquals(0f, MathUtil.clamp(-1f, 0f, 1f));
        assertEquals(1f, MathUtil.clamp(2f, 0f, 1f));
        assertEquals(1f, MathUtil.clamp(Float.POSITIVE_INFINITY, 0f, 1f));
        assertEquals(0f, MathUtil.clamp(Float.NEGATIVE_INFINITY, 0f, 1f));
        assertEquals(5f, MathUtil.clamp(5f, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY));
        assertEquals(0.25f, MathUtil.clamp(0.25f, 0.25f, 0.25f));
        // -0 sits below +0 for Math.max and Math.min
        assertEquals(bits(0f), bits(MathUtil.clamp(-0f, 0f, 1f)));
        assertEquals(bits(-0f), bits(MathUtil.clamp(-0f, -1f, 1f)));
        assertEquals(bits(-0f), bits(MathUtil.clamp(0f, -1f, -0f)));
        // NaN in any slot comes out as NaN
        assertTrue(Float.isNaN(MathUtil.clamp(Float.NaN, 0f, 1f)));
        assertTrue(Float.isNaN(MathUtil.clamp(0.5f, Float.NaN, 1f)));
        assertTrue(Float.isNaN(MathUtil.clamp(0.5f, 0f, Float.NaN)));
    }

    @Test
    void floatClampWithInvertedBoundsReturnsMax() {
        assertEquals(0f, MathUtil.clamp(0.5f, 1f, 0f));
        assertEquals(0f, MathUtil.clamp(2f, 1f, 0f));
        assertEquals(0f, MathUtil.clamp(-2f, 1f, 0f));
    }

    @Test
    void floatClampMatchesTheJdkClampOnEveryOrderedEdgeTriple() {
        for (float value : EDGE_FLOATS) {
            for (float min : EDGE_FLOATS) {
                for (float max : EDGE_FLOATS) {
                    if (min != min || max != max || Float.compare(min, max) > 0) {
                        continue;
                    }
                    assertEquals(Float.floatToIntBits(Math.clamp(value, min, max)), Float.floatToIntBits(MathUtil.clamp(value, min, max)),
                            () -> "clamp(" + value + ", " + min + ", " + max + ")");
                }
            }
        }
    }

    @Test
    void saturateClampsToTheUnitRange() {
        assertEquals(0.5f, MathUtil.saturate(0.5f));
        assertEquals(0f, MathUtil.saturate(-1f));
        assertEquals(1f, MathUtil.saturate(2f));
        assertEquals(1f, MathUtil.saturate(1f));
        assertEquals(bits(0f), bits(MathUtil.saturate(0f)));
        assertEquals(Math.nextDown(1f), MathUtil.saturate(Math.nextDown(1f)));
        assertEquals(1f, MathUtil.saturate(Math.nextUp(1f)));
        assertEquals(Float.MIN_VALUE, MathUtil.saturate(Float.MIN_VALUE));
        assertEquals(bits(0f), bits(MathUtil.saturate(-Float.MIN_VALUE)));
        assertEquals(1f, MathUtil.saturate(Float.MAX_VALUE));
        assertEquals(0f, MathUtil.saturate(-Float.MAX_VALUE));
        assertEquals(1f, MathUtil.saturate(Float.POSITIVE_INFINITY));
        assertEquals(0f, MathUtil.saturate(Float.NEGATIVE_INFINITY));
    }

    @Test
    void saturateSendsNaNAndNegativeZeroToPositiveZero() {
        assertEquals(bits(0f), bits(MathUtil.saturate(-0f)));
        assertEquals(bits(0f), bits(MathUtil.saturate(Float.NaN)));
        assertEquals(bits(0f), bits(MathUtil.saturate(Float.intBitsToFloat(0x7FC00001))));
        assertEquals(bits(0f), bits(MathUtil.saturate(Float.intBitsToFloat(0xFFFFFFFF))));
    }

    @Test
    void saturateSweepOverEveryFloat() throws ReflectiveOperationException {
        assertSweepClean("saturateMismatch");
    }

    @Test
    void roughlyEqualUsesAStrictAbsoluteTolerance() {
        assertTrue(MathUtil.roughlyEqual(1f, 1.000001f));
        assertFalse(MathUtil.roughlyEqual(1f, 1.1f));
        assertTrue(MathUtil.roughlyEqual(0f, 0f));
        assertTrue(MathUtil.roughlyEqual(0f, -0f));
        assertTrue(MathUtil.roughlyEqual(0f, 9.9e-6f));
        assertTrue(MathUtil.roughlyEqual(Math.nextDown(1e-5f), 0f));
        assertFalse(MathUtil.roughlyEqual(0f, 1e-5f));
        assertFalse(MathUtil.roughlyEqual(0f, -1e-5f));
        assertFalse(MathUtil.roughlyEqual(1e-5f, 0f));
        // absolute, not relative: neighbouring floats at 1e7 are a whole unit apart, while 1e-7 and 2e-7 match
        assertFalse(MathUtil.roughlyEqual(1e7f, Math.nextUp(1e7f)));
        assertTrue(MathUtil.roughlyEqual(1e-7f, 2e-7f));
        assertTrue(MathUtil.roughlyEqual(Float.MAX_VALUE, Float.MAX_VALUE));
        assertFalse(MathUtil.roughlyEqual(Float.MAX_VALUE, -Float.MAX_VALUE));
    }

    @Test
    void roughlyEqualMatchesEqualInfinitiesButNeverNaN() {
        assertTrue(MathUtil.roughlyEqual(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY));
        assertTrue(MathUtil.roughlyEqual(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY));
        assertFalse(MathUtil.roughlyEqual(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY));
        assertFalse(MathUtil.roughlyEqual(Float.POSITIVE_INFINITY, Float.MAX_VALUE));
        assertFalse(MathUtil.roughlyEqual(Float.NaN, Float.NaN));
        assertFalse(MathUtil.roughlyEqual(Float.NaN, 0f));
        assertFalse(MathUtil.roughlyEqual(0f, Float.NaN));
        assertFalse(MathUtil.roughlyEqual(Float.NaN, Float.POSITIVE_INFINITY));
    }

    @Test
    void roughlyEqualIsSymmetricOnEveryEdgePair() {
        for (float a : EDGE_FLOATS) {
            for (float b : EDGE_FLOATS) {
                assertEquals(MathUtil.roughlyEqual(a, b), MathUtil.roughlyEqual(b, a), () -> a + " vs " + b);
            }
        }
    }

    @Test
    void roughlyEqualSweepOverEveryFloatAndItsNeighbour() throws ReflectiveOperationException {
        assertSweepClean("roughlyEqualMismatch");
    }

    @Test
    void nearestToZeroIsZeroWhenTheRangeHoldsIt() {
        assertEquals(0, MathUtil.nearestToZero(0, 0));
        assertEquals(0, MathUtil.nearestToZero(-5, 5));
        assertEquals(0, MathUtil.nearestToZero(0, 5));
        assertEquals(0, MathUtil.nearestToZero(-5, 0));
        assertEquals(0, MathUtil.nearestToZero(-8, 8));
        assertEquals(0, MathUtil.nearestToZero(MIN, MAX));
        assertEquals(0, MathUtil.nearestToZero(MIN, 0));
        assertEquals(0, MathUtil.nearestToZero(0, MAX));
    }

    @Test
    void nearestToZeroIsTheEndpointCloserToZeroOtherwise() {
        assertEquals(3, MathUtil.nearestToZero(3, 7));
        assertEquals(-3, MathUtil.nearestToZero(-7, -3));
        assertEquals(1, MathUtil.nearestToZero(1, 1));
        assertEquals(-1, MathUtil.nearestToZero(-1, -1));
        assertEquals(MAX, MathUtil.nearestToZero(MAX, MAX));
        assertEquals(MIN, MathUtil.nearestToZero(MIN, MIN));
        assertEquals(-1, MathUtil.nearestToZero(MIN, -1));
        assertEquals(1, MathUtil.nearestToZero(1, MAX));
        // a section's 16-block span either side of the camera
        assertEquals(16, MathUtil.nearestToZero(16, 32));
        assertEquals(-16, MathUtil.nearestToZero(-32, -16));
    }

    @Test
    void nearestToZeroEqualsClampingZeroOnEveryOrderedRange() {
        for (int min : EDGE_INTS) {
            for (int max : EDGE_INTS) {
                if (min <= max) {
                    checkNearestToZero(min, max);
                }
            }
        }
        SplittableRandom random = new SplittableRandom(0x2E0);
        for (int i = 0; i < 100_000; i++) {
            int a = random.nextInt();
            int b = random.nextInt();
            checkNearestToZero(Math.min(a, b), Math.max(a, b));
            int origin = random.nextInt(-1 << 12, 1 << 12);
            checkNearestToZero(origin, origin + 16);
        }
    }

    private static void checkNearestToZero(int min, int max) {
        int nearest = MathUtil.nearestToZero(min, max);
        assertEquals(Math.min(Math.max(0, min), max), nearest, () -> "nearestToZero(" + min + ", " + max + ")");
        assertEquals(MathUtil.clamp(0, min, max), nearest);
    }
}
