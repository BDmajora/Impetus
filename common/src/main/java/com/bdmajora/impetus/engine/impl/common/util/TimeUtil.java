package com.bdmajora.impetus.engine.impl.common.util;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

// Formats durations as text with four significant digits and a unit abbreviation
public final class TimeUtil {
    // Every time unit ordered from finest to coarsest
    private static final TimeUnit[] UNITS = TimeUnit.values();
    // Number of time units
    private static final int COUNT = UNITS.length;
    // Row length of the ratio table
    private static final int STRIDE = COUNT + 1;
    // Unsigned maximum stored after the last ratio of each row
    private static final long ROW_END = -1L;
    // Length of each coarser unit in finer units indexed by finer ordinal times the stride plus coarser ordinal
    private static final long[] RATIOS = new long[COUNT * STRIDE];
    // Unit abbreviation bytes with a leading space indexed by ordinal
    private static final byte[][] SUFFIXES = new byte[COUNT][];
    // Exponent of the first entry in the power table
    private static final int LOWEST_POWER = -29;
    // Nearest doubles to the powers of ten with exponents between minus 29 and 33 indexed by exponent minus the lowest exponent
    private static final double[] POWERS = new double[63];
    // Largest exponent whose power of ten is an exact double
    private static final int EXACT_POWER = 22;
    // Longest formatted number in bytes
    private static final int NUMBER_LENGTH = 10;

    // Fills the ratio, suffix and power tables
    static {
        for (int fine = 0; fine < COUNT; fine++) {
            for (int coarse = fine; coarse < COUNT; coarse++) {
                RATIOS[fine * STRIDE + coarse] = UNITS[fine].convert(1, UNITS[coarse]);
            }
            RATIOS[fine * STRIDE + COUNT] = ROW_END;
            SUFFIXES[fine] = (" " + abbreviation(UNITS[fine])).getBytes(StandardCharsets.ISO_8859_1);
        }
        for (int i = 0; i < POWERS.length; i++) {
            POWERS[i] = Double.parseDouble("1e" + (i + LOWEST_POWER));
        }
    }

    // Prevents instantiation
    private TimeUtil() {
    }

    // Returns the abbreviation of a time unit
    private static String abbreviation(TimeUnit unit) {
        return switch (unit) {
            case NANOSECONDS -> "ns";
            case MICROSECONDS -> "us";
            case MILLISECONDS -> "ms";
            case SECONDS -> "s";
            case MINUTES -> "min";
            case HOURS -> "h";
            case DAYS -> "d";
        };
    }

    // Returns the duration as text in the largest unit that keeps its magnitude at least one
    public static String stringifyTime(long duration, TimeUnit unit) {
        int from = Objects.requireNonNull(unit, "unit").ordinal();
        int row = from * STRIDE;
        // Compares the magnitude as an unsigned value and counts the minimum long as 2^63
        long magnitude = Math.abs(duration);
        int to = from;
        while (Long.compareUnsigned(magnitude, RATIOS[row + to + 1]) >= 0) {
            to++;
        }
        return format(duration / (double) RATIOS[row + to], to);
    }

    // Returns the duration as text in the display unit
    public static String stringifyTime(long duration, TimeUnit unit, TimeUnit displayUnit) {
        int from = Objects.requireNonNull(unit, "unit").ordinal();
        int to = Objects.requireNonNull(displayUnit, "displayUnit").ordinal();
        // Divides by the ratio for a coarser display unit and multiplies by it for a finer one
        double value = to >= from ? duration / (double) RATIOS[from * STRIDE + to] : duration * (double) RATIOS[to * STRIDE + from];
        return format(value, to);
    }

    // Returns the nearest double to ten raised to the exponent
    private static double power(int exponent) {
        return POWERS[exponent - LOWEST_POWER];
    }

    // Returns the nearest double to the significand times ten raised to the exponent
    private static double scale(double significand, int exponent) {
        if (exponent < 0) {
            return significand / power(-exponent);
        }
        if (exponent <= EXACT_POWER) {
            return significand * power(exponent);
        }
        return significand * power(exponent - EXACT_POWER) * power(EXACT_POWER);
    }

    // Returns the value rounded half up to four significant digits in the Formatter general style followed by the unit suffix
    private static String format(double value, int unit) {
        byte[] suffix = SUFFIXES[unit];
        byte[] out = new byte[NUMBER_LENGTH + suffix.length];
        int length = 0;
        if (value < 0) {
            out[length++] = '-';
        }
        if (value == 0) {
            // Writes zero with three zero decimals
            out[length++] = '0';
            out[length++] = '.';
            out[length++] = '0';
            out[length++] = '0';
            out[length++] = '0';
        } else {
            double magnitude = Math.abs(value);
            // Estimates the decimal exponent from the binary exponent and corrects it by one power of ten
            int power = (Math.getExponent(magnitude) * 78913) >> 18;
            if (magnitude >= power(power + 1)) {
                power++;
            }
            // Truncates the magnitude to four digits and rounds up when it reaches the nearest double to the halfway decimal
            int digits = (int) (magnitude * power(3 - power));
            if (magnitude >= scale(10.0 * digits + 5, power - 4)) {
                digits++;
            }
            // Carries a rounded 10000 into the next power of ten
            if (digits == 10000) {
                digits = 1000;
                power++;
            }
            if (power >= -4 && power <= 3) {
                // Writes fixed point text with the point after the integer digits
                int pointAt = power;
                if (power < 0) {
                    // Writes the leading zeros of a value below one
                    out[length++] = '0';
                    out[length++] = '.';
                    for (int zero = power; zero < -1; zero++) {
                        out[length++] = '0';
                    }
                    pointAt = 3;
                }
                length = writeDigits(out, length, digits, pointAt);
            } else {
                // Writes scientific text with a signed two digit exponent
                length = writeDigits(out, length, digits, 0);
                out[length++] = 'e';
                out[length++] = (byte) (power < 0 ? '-' : '+');
                int exponent = Math.abs(power);
                out[length++] = (byte) ('0' + exponent / 10);
                out[length++] = (byte) ('0' + exponent % 10);
            }
        }
        System.arraycopy(suffix, 0, out, length, suffix.length);
        return new String(out, 0, length + suffix.length, StandardCharsets.ISO_8859_1);
    }

    // Writes the four digits with a point after index pointAt when pointAt is below 3 and returns the new length
    private static int writeDigits(byte[] out, int length, int digits, int pointAt) {
        out[length++] = (byte) ('0' + digits / 1000);
        if (pointAt == 0) {
            out[length++] = '.';
        }
        out[length++] = (byte) ('0' + digits / 100 % 10);
        if (pointAt == 1) {
            out[length++] = '.';
        }
        out[length++] = (byte) ('0' + digits / 10 % 10);
        if (pointAt == 2) {
            out[length++] = '.';
        }
        out[length++] = (byte) ('0' + digits % 10);
        return length;
    }
}
