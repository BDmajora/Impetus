package com.bdmajora.impetus.engine.impl.common.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests the duration formatting of TimeUtil
class TimeUtilTest {
    // Every time unit ordered from finest to coarsest
    private static final TimeUnit[] UNITS = TimeUnit.values();
    // Expected unit abbreviations indexed by ordinal
    private static final String[] ABBREVIATIONS = {"ns", "us", "ms", "s", "min", "h", "d"};
    // Durations on the zero, sign, rounding, int, long and double precision boundaries
    private static final long[] EDGE_DURATIONS = {
            0, 1, -1, 2, -2, 5, 9, 10, 99, 100, 999, 1000, 1001, 9999, 10_000, 99_995, 999_950, 1_000_500, 9_999_500,
            Integer.MAX_VALUE, Integer.MIN_VALUE, 1L << 31, -(1L << 31), 1L << 53, (1L << 53) + 1, -(1L << 53) - 1,
            Long.MAX_VALUE, Long.MAX_VALUE - 1, Long.MIN_VALUE, Long.MIN_VALUE + 1
    };

    // Returns the largest unit whose conversion of the duration is positive
    private static TimeUnit positiveUnit(long duration, TimeUnit unit) {
        for (int i = UNITS.length - 1; i > unit.ordinal(); i--) {
            if (UNITS[i].convert(duration, unit) > 0) {
                return UNITS[i];
            }
        }
        return unit;
    }

    // Returns the largest unit that keeps the magnitude of the duration at least one
    private static TimeUnit magnitudeUnit(long duration, TimeUnit unit) {
        return positiveUnit(duration == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(duration), unit);
    }

    // Returns the duration converted to the display unit as a double
    private static double value(long duration, TimeUnit unit, TimeUnit displayUnit) {
        if (displayUnit.compareTo(unit) >= 0) {
            return duration / (double) unit.convert(1, displayUnit);
        }
        return duration * (double) displayUnit.convert(1, unit);
    }

    // Returns the String.format general text with four significant digits followed by the abbreviation
    private static String reference(double value, TimeUnit displayUnit) {
        return String.format(Locale.ROOT, "%.4g %s", value, ABBREVIATIONS[displayUnit.ordinal()]);
    }

    // Asserts both overloads against the reference text for every pair of units
    private static void assertMatchesReference(long duration) {
        for (TimeUnit unit : UNITS) {
            TimeUnit chosen = magnitudeUnit(duration, unit);
            assertEquals(reference(value(duration, unit, chosen), chosen), TimeUtil.stringifyTime(duration, unit), () -> duration + " " + unit);
            for (TimeUnit display : UNITS) {
                assertEquals(reference(value(duration, unit, display), display), TimeUtil.stringifyTime(duration, unit, display),
                        () -> duration + " " + unit + " in " + display);
            }
        }
    }

    // Checks that a null unit or display unit throws with the parameter name
    @Test
    void nullUnitsAreRejected() {
        assertEquals("unit", assertThrows(NullPointerException.class, () -> TimeUtil.stringifyTime(1, null)).getMessage());
        assertEquals("unit", assertThrows(NullPointerException.class, () -> TimeUtil.stringifyTime(1, null, MILLISECONDS)).getMessage());
        assertEquals("unit", assertThrows(NullPointerException.class, () -> TimeUtil.stringifyTime(1, null, null)).getMessage());
        assertEquals("displayUnit", assertThrows(NullPointerException.class, () -> TimeUtil.stringifyTime(1, MILLISECONDS, null)).getMessage());
        assertEquals("unit", assertThrows(NullPointerException.class, () -> TimeUtil.stringifyTime(0, null)).getMessage());
    }

    // Checks the abbreviation of every unit in both overloads
    @Test
    void everyUnitUsesItsAbbreviation() {
        assertEquals(List.of("1.000 ns", "1.000 us", "1.000 ms", "1.000 s", "1.000 min", "1.000 h", "1.000 d"),
                Arrays.stream(UNITS).map(unit -> TimeUtil.stringifyTime(1, unit)).toList());
        for (TimeUnit unit : UNITS) {
            assertEquals("1.000 " + ABBREVIATIONS[unit.ordinal()], TimeUtil.stringifyTime(1, unit, unit));
        }
        assertEquals("1.000 us", TimeUtil.stringifyTime(1000, NANOSECONDS, MICROSECONDS));
        assertEquals("2.000 min", TimeUtil.stringifyTime(120, SECONDS, MINUTES));
        assertEquals("1.000 h", TimeUtil.stringifyTime(60, MINUTES, HOURS));
    }

    // Checks that zero keeps its source unit and prints four digits in every display unit
    @Test
    void zeroPrintsFourDigitsInEveryUnit() {
        for (TimeUnit unit : UNITS) {
            assertEquals("0.000 " + ABBREVIATIONS[unit.ordinal()], TimeUtil.stringifyTime(0, unit));
            for (TimeUnit display : UNITS) {
                assertEquals("0.000 " + ABBREVIATIONS[display.ordinal()], TimeUtil.stringifyTime(0, unit, display), unit + " in " + display);
            }
        }
    }

    // Checks the automatic unit on both sides of every threshold between two units
    @Test
    void automaticUnitSwitchesAtEveryExactThreshold() {
        for (TimeUnit unit : UNITS) {
            for (int to = unit.ordinal() + 1; to < UNITS.length; to++) {
                long ratio = unit.convert(1, UNITS[to]);
                String below = " " + ABBREVIATIONS[to - 1];
                assertEquals("1.000 " + ABBREVIATIONS[to], TimeUtil.stringifyTime(ratio, unit), unit + " to " + UNITS[to]);
                assertEquals("-1.000 " + ABBREVIATIONS[to], TimeUtil.stringifyTime(-ratio, unit), unit + " to " + UNITS[to]);
                assertTrue(TimeUtil.stringifyTime(ratio - 1, unit).endsWith(below), unit + " below " + UNITS[to]);
                assertTrue(TimeUtil.stringifyTime(1 - ratio, unit).endsWith(below), unit + " below " + UNITS[to]);
                assertMatchesReference(ratio - 1);
                assertMatchesReference(ratio);
                assertMatchesReference(ratio + 1);
            }
        }
        assertEquals("1.500 ms", TimeUtil.stringifyTime(1_500_000, NANOSECONDS));
        assertEquals("2.000 s", TimeUtil.stringifyTime(2000, MILLISECONDS));
        assertEquals("500.0 ns", TimeUtil.stringifyTime(500, NANOSECONDS));
        assertEquals("3.000 d", TimeUtil.stringifyTime(3, DAYS));
        assertEquals("1.000 ns", TimeUtil.stringifyTime(1, NANOSECONDS));
    }

    // Checks that a negative duration prints the minus sign before the text of its magnitude
    @Test
    void negativeDurationsMirrorTheirMagnitude() {
        assertEquals("-1.500 ms", TimeUtil.stringifyTime(-1_500_000, NANOSECONDS));
        assertEquals("-2.000 min", TimeUtil.stringifyTime(-120, SECONDS));
        assertEquals("-1.000 ns", TimeUtil.stringifyTime(-1, NANOSECONDS));
        for (long duration : EDGE_DURATIONS) {
            if (duration <= 0) {
                continue;
            }
            for (TimeUnit unit : UNITS) {
                assertEquals("-" + TimeUtil.stringifyTime(duration, unit), TimeUtil.stringifyTime(-duration, unit), duration + " " + unit);
                for (TimeUnit display : UNITS) {
                    assertEquals("-" + TimeUtil.stringifyTime(duration, unit, display), TimeUtil.stringifyTime(-duration, unit, display));
                }
            }
        }
    }

    // Checks the long limits in the automatic and fixed overloads
    @Test
    void longLimitsPrintWithoutOverflow() {
        assertEquals("1.068e+05 d", TimeUtil.stringifyTime(Long.MAX_VALUE, NANOSECONDS));
        assertEquals("-1.068e+05 d", TimeUtil.stringifyTime(Long.MIN_VALUE, NANOSECONDS));
        assertEquals("-1.068e+05 d", TimeUtil.stringifyTime(Long.MIN_VALUE + 1, NANOSECONDS));
        assertEquals("1.068e+11 d", TimeUtil.stringifyTime(Long.MAX_VALUE, MILLISECONDS));
        assertEquals("9.223e+18 d", TimeUtil.stringifyTime(Long.MAX_VALUE, DAYS));
        assertEquals("-9.223e+18 d", TimeUtil.stringifyTime(Long.MIN_VALUE, DAYS));
        assertEquals("7.969e+32 ns", TimeUtil.stringifyTime(Long.MAX_VALUE, DAYS, NANOSECONDS));
        assertEquals("-7.969e+32 ns", TimeUtil.stringifyTime(Long.MIN_VALUE, DAYS, NANOSECONDS));
        assertEquals("3.320e+31 ns", TimeUtil.stringifyTime(Long.MAX_VALUE, HOURS, NANOSECONDS));
        assertEquals("1.157e-14 d", TimeUtil.stringifyTime(1, NANOSECONDS, DAYS));
        assertEquals("-1.157e-14 d", TimeUtil.stringifyTime(-1, NANOSECONDS, DAYS));
    }

    // Checks the int limits as durations
    @Test
    void intLimitsPrintLikeAnyOtherDuration() {
        assertEquals("2.147 s", TimeUtil.stringifyTime(Integer.MAX_VALUE, NANOSECONDS));
        assertEquals("-2.147 s", TimeUtil.stringifyTime(Integer.MIN_VALUE, NANOSECONDS));
        assertEquals("24.86 d", TimeUtil.stringifyTime(Integer.MAX_VALUE, MILLISECONDS, DAYS));
        assertMatchesReference(Integer.MAX_VALUE);
        assertMatchesReference(Integer.MIN_VALUE);
        assertMatchesReference(Integer.MAX_VALUE + 1L);
        assertMatchesReference(Integer.MIN_VALUE - 1L);
    }

    // Checks display units finer than the source unit
    @Test
    void finerDisplayUnitsScaleTheDurationUp() {
        assertEquals("1000 ms", TimeUtil.stringifyTime(1, SECONDS, MILLISECONDS));
        assertEquals("8.640e+13 ns", TimeUtil.stringifyTime(1, DAYS, NANOSECONDS));
        assertEquals("-180.0 min", TimeUtil.stringifyTime(-3, HOURS, MINUTES));
        assertEquals("120.0 s", TimeUtil.stringifyTime(2, MINUTES, SECONDS));
        assertEquals("7000 ns", TimeUtil.stringifyTime(7, MICROSECONDS, NANOSECONDS));
        assertEquals("1.440e+04 min", TimeUtil.stringifyTime(10, DAYS, MINUTES));
    }

    // Checks half up rounding on the shortest decimal digits of the value
    @Test
    void roundsHalfUpOnTheShortestDecimalDigits() {
        assertEquals("1.001 ms", TimeUtil.stringifyTime(1_000_500, NANOSECONDS));
        assertEquals("1.000 ms", TimeUtil.stringifyTime(1_000_499, NANOSECONDS));
        assertEquals("1.235 ms", TimeUtil.stringifyTime(1_234_500, NANOSECONDS));
        assertEquals("12.35 us", TimeUtil.stringifyTime(12_345, NANOSECONDS));
        assertEquals("12.34 us", TimeUtil.stringifyTime(12_344, NANOSECONDS));
        assertEquals("-12.35 us", TimeUtil.stringifyTime(-12_345, NANOSECONDS));
    }

    // Checks rounding that carries into the next power of ten
    @Test
    void roundingCarriesIntoTheNextPowerOfTen() {
        assertEquals("10.00 ms", TimeUtil.stringifyTime(9_999_500, NANOSECONDS));
        assertEquals("100.0 us", TimeUtil.stringifyTime(99_995, NANOSECONDS));
        assertEquals("1000 us", TimeUtil.stringifyTime(999_950, NANOSECONDS));
        assertEquals("9.999 us", TimeUtil.stringifyTime(9_999, NANOSECONDS));
        assertEquals("100.0 s", TimeUtil.stringifyTime(99_995, MILLISECONDS, SECONDS));
        assertEquals("1.000e+07 ns", TimeUtil.stringifyTime(9_999_500, NANOSECONDS, NANOSECONDS));
        assertEquals("0.0001000 s", TimeUtil.stringifyTime(99_995, NANOSECONDS, SECONDS));
    }

    // Checks the switch between fixed point and scientific text at both ends
    @Test
    void fixedPointCoversTenToTheMinusFourUpToTenThousand() {
        assertEquals("0.0001000 ms", TimeUtil.stringifyTime(100, NANOSECONDS, MILLISECONDS));
        assertEquals("9.900e-05 ms", TimeUtil.stringifyTime(99, NANOSECONDS, MILLISECONDS));
        assertEquals("9.999e-05 s", TimeUtil.stringifyTime(99_994, NANOSECONDS, SECONDS));
        assertEquals("0.001000 us", TimeUtil.stringifyTime(1, NANOSECONDS, MICROSECONDS));
        assertEquals("1.000e-06 ms", TimeUtil.stringifyTime(1, NANOSECONDS, MILLISECONDS));
        assertEquals("9999 ms", TimeUtil.stringifyTime(9_999, MILLISECONDS, MILLISECONDS));
        assertEquals("1.000e+04 ms", TimeUtil.stringifyTime(10_000, MILLISECONDS, MILLISECONDS));
        assertEquals("1.235e+04 ms", TimeUtil.stringifyTime(12_345, MILLISECONDS, MILLISECONDS));
        assertEquals("1234 ns", TimeUtil.stringifyTime(1_234, NANOSECONDS, NANOSECONDS));
        assertEquals("123.0 ns", TimeUtil.stringifyTime(123, NANOSECONDS, NANOSECONDS));
        assertEquals("12.00 ns", TimeUtil.stringifyTime(12, NANOSECONDS, NANOSECONDS));
        assertEquals("-0.0001000 ms", TimeUtil.stringifyTime(-100, NANOSECONDS, MILLISECONDS));
    }

    // Checks every edge duration and its negation against the reference text
    @Test
    void edgeDurationsMatchTheReference() {
        for (long duration : EDGE_DURATIONS) {
            assertMatchesReference(duration);
            assertMatchesReference(-duration);
        }
    }

    // Checks every nanosecond duration up to 1.1 ms against the reference text in the automatic unit and in milliseconds
    @Test
    void denseNanosecondDurationsMatchTheReference() {
        for (long duration = 0; duration <= 1_100_000; duration++) {
            long d = duration;
            TimeUnit chosen = positiveUnit(duration, NANOSECONDS);
            assertEquals(reference(value(duration, NANOSECONDS, chosen), chosen), TimeUtil.stringifyTime(duration, NANOSECONDS), () -> d + " ns");
            assertEquals(reference(duration / 1e6, MILLISECONDS), TimeUtil.stringifyTime(duration, NANOSECONDS, MILLISECONDS), () -> d + " ns in ms");
        }
    }

    // Checks halfway and carry durations at every power of ten against the reference text
    @Test
    void halfwayAndCarryDurationsMatchTheReference() {
        long scale = 1;
        for (int power = 0; power <= 14; power++, scale *= 10) {
            for (long significand : new long[]{10_005, 12_345, 49_995, 99_985, 99_995}) {
                for (long offset = -1; offset <= 1; offset++) {
                    assertMatchesReference(significand * scale + offset);
                    assertMatchesReference(-significand * scale + offset);
                }
            }
        }
    }

    // Checks random durations of every magnitude against the reference text
    @Test
    void randomDurationsMatchTheReference() {
        SplittableRandom random = new SplittableRandom(0x7153_0026L);
        for (int i = 0; i < 4000; i++) {
            assertMatchesReference(random.nextLong() >> random.nextInt(64));
        }
    }

    // Checks that repeated and interleaved calls return the same text
    @Test
    void repeatedCallsReturnTheSameText() {
        String first = TimeUtil.stringifyTime(1_500_000, NANOSECONDS);
        assertEquals("1.000e+04 ms", TimeUtil.stringifyTime(10_000, MILLISECONDS, MILLISECONDS));
        assertEquals("-7.969e+32 ns", TimeUtil.stringifyTime(Long.MIN_VALUE, DAYS, NANOSECONDS));
        for (int i = 0; i < 1000; i++) {
            assertEquals(first, TimeUtil.stringifyTime(1_500_000, NANOSECONDS));
        }
    }

    // Checks that parallel calls from several threads match the sequential text
    @Test
    void concurrentCallsMatchSequentialCalls() throws Exception {
        SplittableRandom random = new SplittableRandom(26);
        long[] durations = new long[2000];
        String[] expected = new String[durations.length];
        for (int i = 0; i < durations.length; i++) {
            durations[i] = random.nextLong() >> random.nextInt(64);
            expected[i] = TimeUtil.stringifyTime(durations[i], NANOSECONDS) + TimeUtil.stringifyTime(durations[i], SECONDS, MILLISECONDS);
        }
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                results.add(pool.submit(() -> {
                    start.await();
                    int mismatches = 0;
                    for (int round = 0; round < 20; round++) {
                        for (int i = 0; i < durations.length; i++) {
                            String text = TimeUtil.stringifyTime(durations[i], NANOSECONDS) + TimeUtil.stringifyTime(durations[i], SECONDS, MILLISECONDS);
                            if (!text.equals(expected[i])) {
                                mismatches++;
                            }
                        }
                    }
                    return mismatches;
                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertEquals(0, result.get(1, TimeUnit.MINUTES));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // Checks that the text ignores the default locale
    @Test
    void textIgnoresTheDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            for (Locale locale : new Locale[]{Locale.GERMANY, Locale.forLanguageTag("ar-EG"), Locale.forLanguageTag("hi-IN-u-nu-deva")}) {
                Locale.setDefault(locale);
                assertEquals("1.500 ms", TimeUtil.stringifyTime(1_500_000, NANOSECONDS), locale::toString);
                assertEquals("-1.235e+04 ms", TimeUtil.stringifyTime(-12_345, MILLISECONDS, MILLISECONDS), locale::toString);
            }
        } finally {
            Locale.setDefault(original);
        }
    }

    // Checks that the class is final with one private constructor and only static members
    @Test
    void utilityClassIsFinalWithAPrivateConstructorAndOnlyStaticMembers() throws ReflectiveOperationException {
        assertTrue(Modifier.isFinal(TimeUtil.class.getModifiers()));
        Constructor<?>[] constructors = TimeUtil.class.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        assertEquals(0, constructors[0].getParameterCount());
        constructors[0].setAccessible(true);
        assertNotNull(constructors[0].newInstance());
        // Skips the synthetic members JaCoCo adds to the loaded class
        List<Method> methods = Arrays.stream(TimeUtil.class.getDeclaredMethods()).filter(m -> !m.isSynthetic()).toList();
        assertEquals(List.of("stringifyTime", "stringifyTime"),
                methods.stream().filter(m -> Modifier.isPublic(m.getModifiers())).map(Method::getName).toList());
        for (Method method : methods) {
            assertTrue(Modifier.isStatic(method.getModifiers()), method.getName());
        }
        for (Field field : TimeUtil.class.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                assertTrue(Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers()), field.getName());
            }
        }
    }
}
