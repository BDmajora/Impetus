package com.bdmajora.equilibrium.common.util.math;


// Sine table that stores one quarter of the values.
public class CompactSineLUT {
    // Bits of the first quarter of the base game table.
    private static final int[] SINE_TABLE_INT = new int[16384 + 1];

    // Value at the half turn.
    private static float sineTableMidpoint;

    // Prevents instantiation
    private CompactSineLUT() {
    }

    // Fills the table and rejects a table that differs from the base game.
    public static void init(float[] vanilla) {
        if (vanilla == null || vanilla.length != 65536) {
            throw new IllegalStateException("Expected a 65536-entry vanilla sine table, found "
                    + (vanilla == null ? "null" : vanilla.length + " entries"));
        }

        for (int i = 0; i < SINE_TABLE_INT.length; i++) {
            SINE_TABLE_INT[i] = Float.floatToRawIntBits(vanilla[i]);
        }

        sineTableMidpoint = vanilla[vanilla.length / 2];

        for (int i = 0; i < vanilla.length; i++) {
            float expected = vanilla[i];
            float value = lookup(i);

            if (expected != value) {
                throw new IllegalStateException(String.format(
                        "Sine LUT error at index %d (expected: %s, found: %s)", i, expected, value));
            }
        }
    }

    // Returns the sine of the angle.
    public static float sin(float value) {
        return lookup((int) (value * 10430.378F) & 65535);
    }

    // Returns the cosine of the angle.
    public static float cos(float value) {
        return lookup((int) (value * 10430.378F + 16384.0F) & 65535);
    }

    // Returns the table value of the index.
    private static float lookup(int index) {
        if (index == 32768) {
            return sineTableMidpoint;
        }

        int neg = (index & 0x8000) << 16;

        int mask = (index << 17) >> 31;

        int pos = (0x8001 & mask) + (index ^ mask);

        pos &= 0x7fff;

        return Float.intBitsToFloat(SINE_TABLE_INT[pos] ^ neg);
    }
}
