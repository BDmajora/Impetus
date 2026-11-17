package com.bdmajora.impetus.engine.impl.util;

// Branch free comparisons.
public class BitwiseMath {
    // Returns one when a is less than b and zero otherwise.
    public static int lessThan(int a, int b) {
        return (int) (((long) a - b) >>> 63);
    }

    // Returns one when a is greater than b and zero otherwise.
    public static int greaterThan(int a, int b) {
        return (int) (((long) b - a) >>> 63);
    }
}