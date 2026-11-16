package com.bdmajora.extras.client.booster;

// Math helpers that avoid library calls.
public final class FastMath {
    // Whether the helpers are used.
    public static boolean enabled;
    // Largest turn count that fits an int cast.
    private static final int WHOLE_LIMIT = 1 << 30;

    // Prevents instantiation
    private FastMath() {
    }

    // Returns the angle wrapped into the range from minus 180 up to 180.
    public static float wrapDegrees(float value) {
        float turns = (value + 180.0F) * (1.0F / 360.0F);
        if (Math.abs(turns) >= WHOLE_LIMIT) {
            return value - (float) Math.floor(turns) * 360.0F;
        }
        int whole = (int) turns;
        if (turns < whole) {
            whole--;
        }
        float wrapped = value - whole * 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        } else if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    // Returns the angle wrapped into the range from minus 180 up to 180.
    public static double wrapDegrees(double value) {
        double turns = (value + 180.0D) * (1.0D / 360.0D);
        if (Math.abs(turns) >= WHOLE_LIMIT) {
            return value - Math.floor(turns) * 360.0D;
        }
        int whole = (int) turns;
        if (turns < whole) {
            whole--;
        }
        double wrapped = value - whole * 360.0D;
        if (wrapped >= 180.0D) {
            wrapped -= 360.0D;
        } else if (wrapped < -180.0D) {
            wrapped += 360.0D;
        }
        return wrapped;
    }

    // Returns the base two logarithm rounded up.
    public static int ceilLog2(int value) {
        return value <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(value - 1);
    }

    // Returns the base two logarithm rounded down or minus one when the value is not positive.
    public static int floorLog2(int value) {
        return value <= 0 ? -1 : 31 - Integer.numberOfLeadingZeros(value);
    }
}
