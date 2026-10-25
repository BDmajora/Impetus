package com.bdmajora.impetus.engine.impl.common.util;

// Integer and float math helpers
public final class MathUtil {
    // Prevents instantiation
    private MathUtil() {
    }

    // Returns true when the value is a positive power of two
    public static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    // Returns the greatest common divisor of the magnitudes and throws when the result is 2^31
    public static int gcd(int a, int b) {
        int gcd = unsignedGcd(a, b);
        if (gcd < 0) {
            throw new ArithmeticException("gcd(" + a + ", " + b + ") is 2^31, which overflows int");
        }
        return gcd;
    }

    // Returns the least common multiple of the magnitudes or zero when either is zero and throws when it exceeds the int range
    public static int lcm(int a, int b) {
        if (a == 0 || b == 0) {
            return 0;
        }
        // Computes the product as a long and divides it by the greatest common divisor
        long lcm = Math.abs((long) a * b) / Integer.toUnsignedLong(unsignedGcd(a, b));
        if (lcm > Integer.MAX_VALUE) {
            throw new ArithmeticException("lcm(" + a + ", " + b + ") overflows int");
        }
        return (int) lcm;
    }

    // Returns the greatest common divisor of the magnitudes read as unsigned values using the binary gcd algorithm
    private static int unsignedGcd(int a, int b) {
        a = Math.abs(a);
        b = Math.abs(b);
        if (a == 0 || b == 0) {
            return a | b;
        }
        int aTwos = Integer.numberOfTrailingZeros(a);
        int bTwos = Integer.numberOfTrailingZeros(b);
        // Removes the trailing zero bits with logical shifts
        a >>>= aTwos;
        b >>>= bTwos;
        // Subtracts the smaller odd value from the larger until both are equal
        while (a != b) {
            int delta = a - b;
            // Sets a to the difference and b to the smaller value without a branch
            int minDeltaOrZero = delta & (delta >> 31);
            a = delta - minDeltaOrZero - minDeltaOrZero;
            b += minDeltaOrZero;
            a >>= Integer.numberOfTrailingZeros(a);
        }
        // Restores the shared factors of two
        return a << Math.min(aTwos, bTwos);
    }

    // Converts bytes to mebibytes truncating toward zero
    public static long toMib(long bytes) {
        return bytes / (1024L * 1024L);
    }

    // Returns the smallest multiple of a positive factor at or above the value and throws when it exceeds the int range
    public static int roundToward(int value, int factor) {
        if (factor <= 0) {
            throw new IllegalArgumentException("Factor must be positive, got " + factor);
        }
        // Computes the multiple as a long from the ceiling quotient
        long multiple = (long) Math.ceilDiv(value, factor) * factor;
        if (multiple > Integer.MAX_VALUE) {
            throw new ArithmeticException("roundToward(" + value + ", " + factor + ") overflows int");
        }
        return (int) multiple;
    }

    // Returns the value multiplied by itself
    public static float square(float f) {
        return f * f;
    }

    // Returns the floor of the value as an int saturated to the int range with NaN giving zero
    public static int mojfloor(float f) {
        return (int) Math.floor(f);
    }

    // Returns the floor of the value as an int saturated to the int range with NaN giving zero
    public static int mojfloor(double f) {
        return (int) Math.floor(f);
    }

    // Clamps the value between min and max and returns max when the bounds are inverted
    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    // Clamps the value between min and max with NaN giving NaN and returns max when the bounds are inverted
    public static float clamp(float value, float min, float max) {
        return Math.min(Math.max(value, min), max);
    }

    // Clamps the value between zero and one with NaN and negative zero giving positive zero
    public static float saturate(float value) {
        return value > 0.0f ? (value < 1.0f ? value : 1.0f) : 0.0f;
    }

    // Returns true when the values are equal or differ by less than 0.00001
    public static boolean roughlyEqual(float a, float b) {
        return a == b || Math.abs(b - a) < 0.00001f;
    }

    // Returns the value between min and max closest to zero and requires min to be no larger than max
    @SuppressWarnings("ManualMinMaxCalculation")
    public static int nearestToZero(int min, int max) {
        // Compares each bound against zero separately
        int clamped = 0;
        if (min > 0) { clamped = min; }
        if (max < 0) { clamped = max; }
        return clamped;
    }
}