package com.bdmajora.impetus.engine.impl.util.rand;

import it.unimi.dsi.fastutil.HashCommon;

// Minimal SplitMix generator based on the DSI Utilities version.
public class SplitMixRandom {
    // Increment of the state for every value.
    private static final long PHI = 0x9E3779B97F4A7C15L;

    // State of the generator.
    private long x;

    // Creates a generator with a random seed.
    public SplitMixRandom() {
        this(XoRoShiRoRandom.randomSeed());
    }

    // Creates a generator with a seed.
    public SplitMixRandom(final long seed) {
        this.setSeed(seed);
    }

    // Returns the Stafford variant 13 mix of a value.
    private static long staffordMix13(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;

        return z ^ (z >>> 31);
    }

    // Returns the upper 32 bits of the Stafford variant 4 mix of a value.
    private static int staffordMix4Upper32(long z) {
        z = (z ^ (z >>> 33)) * 0x62A9D9ED799705F5L;

        return (int) (((z ^ (z >>> 28)) * 0xCB24D0A5C88C35B3L) >>> 32);
    }

    // Returns the next 64 bit value.
    public long nextLong() {
        return staffordMix13(this.x += PHI);
    }

    // Returns the next 32 bit value.
    public int nextInt() {
        return staffordMix4Upper32(this.x += PHI);
    }

    // Returns a value between zero and the bound without bias.
    public int nextInt(final int n) {
        return (int) this.nextLong(n);
    }

    // Returns a value between zero and the bound without bias and rejects a bound that is not positive.
    public long nextLong(final long n) {
        if (n <= 0) {
            throw new IllegalArgumentException("illegal bound " + n + " (must be positive)");
        }

        long t = staffordMix13(this.x += PHI);

        final long nMinus1 = n - 1;

        if ((n & nMinus1) == 0) {
            return t & nMinus1;
        }

        long u = t >>> 1;

        while (u + nMinus1 - (t = u % n) < 0) {
            u = staffordMix13(this.x += PHI) >>> 1;
        }

        return t;
    }

    // Returns a double of at least zero and below one.
    public double nextDouble() {
        return (staffordMix13(this.x += PHI) >>> 11) * 0x1.0p-53;
    }

    // Returns a float of at least zero and below one.
    public float nextFloat() {
        return (staffordMix4Upper32(this.x += PHI) >>> 8) * 0x1.0p-24f;
    }

    // Returns a random boolean.
    public boolean nextBoolean() {
        return staffordMix4Upper32(this.x += PHI) < 0;
    }

    // Fills the array with random bytes.
    public void nextBytes(final byte[] bytes) {
        int i = bytes.length, n;

        while (i != 0) {
            n = Math.min(i, 8);
            for (long bits = staffordMix13(this.x += PHI); n-- != 0; bits >>= 8) {
                bytes[--i] = (byte) bits;
            }
        }
    }

    // Stores a state derived from the seed.
    public void setSeed(final long seed) {
        this.x = HashCommon.murmurHash3(seed);
    }

    // Stores the state directly.
    public void setState(final long state) {
        this.x = state;
    }
}

