package com.bdmajora.impetus.umbra.util;

// Parses digit strings without throwing on overflow.
public final class SafeInt {
    // Prevents instantiation
    private SafeInt() {
    }

    // Returns the value of a digit string and saturates at the largest int when it is too long.
    public static int parse(String digits) {
        if (digits == null || digits.isEmpty()) {
            throw new NumberFormatException("No digits");
        }
        long value = 0L;
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                throw new NumberFormatException("Not a digit string: " + digits);
            }
            value = value * 10L + (c - '0');
            if (value > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) value;
    }
}
