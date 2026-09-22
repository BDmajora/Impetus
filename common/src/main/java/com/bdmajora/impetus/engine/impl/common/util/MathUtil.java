package com.bdmajora.impetus.engine.impl.common.util;

public class MathUtil {
    // Greater than zero AND a power of two; the zero check matters because the bit trick alone accepts 0
    public static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }

    // Bytes to mebibytes
    public static long toMib(long bytes) {
        return bytes / (1024L * 1024L); // 1 MiB = 1048576 (2^20) bytes
    }

    // Rounds up to a multiple
    public static int roundToward(int value, int factor) {
        return -Math.floorDiv(-value, factor) * factor;
    }

    // f * f
    public static float square(float f) {
        return f * f;
    }

    // Mojang's floor: cast, then subtract one for negatives with a fraction
    public static int mojfloor(float f) {
        int truncated = (int)f;
        return f < (float)truncated ? truncated - 1 : truncated;
    }

    // Mojang's floor, double form
    public static int mojfloor(double f) {
        int truncated = (int)f;
        return f < (double)truncated ? truncated - 1 : truncated;
    }

    // Plain clamp
    public static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    public static float clamp(float value, float min, float max) {
        return Math.min(Math.max(value, min), max);
    }

    // Clamp to [0, 1]
    public static float saturate(float value) {
        return value < 0.0f ? 0.0f : (value > 1.0f ? 1.0f : value);
    }

    // Within a small epsilon
    public static boolean roughlyEqual(float a, float b) {
        return Math.abs(b - a) < 0.00001f;
    }
}
