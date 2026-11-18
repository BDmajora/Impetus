package com.bdmajora.extras.client.culling;

import it.unimi.dsi.fastutil.HashCommon;

import java.util.Arrays;

// Remembers which lattice points were found visible from the camera during one pass.
public final class VisibilityCache {
    // Answer of a point that was not tested this pass.
    public static final int UNKNOWN = -1;
    // Answer of a hidden point.
    public static final int BLOCKED = 0;
    // Answer of a visible point.
    public static final int VISIBLE = 1;
    // Largest number of slots the table grows to.
    static final int MAX_CAPACITY = 1 << 22;

    // Packed point of each slot.
    private long[] keys;
    // Pass stamp of each slot where only the current stamp counts as filled.
    private int[] stamps;
    // Visibility of each slot.
    private boolean[] answers;
    // Number of slots less one.
    private int mask;
    // Number of points stored this pass.
    private int size;
    // Stamp of the current pass.
    private int stamp = 1;

    // Creates a cache with a power of two number of slots.
    public VisibilityCache(int capacity) {
        if (capacity < 2 || capacity > MAX_CAPACITY || Integer.bitCount(capacity) != 1) {
            throw new IllegalArgumentException("Capacity must be a power of two up to " + MAX_CAPACITY + ": " + capacity);
        }
        this.allocate(capacity);
    }

    // Creates empty tables of the given size.
    private void allocate(int capacity) {
        this.keys = new long[capacity];
        this.stamps = new int[capacity];
        this.answers = new boolean[capacity];
        this.mask = capacity - 1;
    }

    // Packs a lattice point into one long.
    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    // Returns the number of slots.
    public int capacity() {
        return this.keys.length;
    }

    // Returns the number of points stored this pass.
    public int size() {
        return this.size;
    }

    // Forgets every point and wipes the stamps when they would wrap.
    public void clear() {
        this.size = 0;
        if (++this.stamp == Integer.MAX_VALUE) {
            Arrays.fill(this.stamps, 0);
            this.stamp = 1;
        }
    }

    // Returns VISIBLE or BLOCKED for a point tested this pass and UNKNOWN otherwise.
    public int get(int x, int y, int z) {
        long key = key(x, y, z);
        int slot = (int) HashCommon.mix(key) & this.mask;
        while (this.stamps[slot] == this.stamp) {
            if (this.keys[slot] == key) {
                return this.answers[slot] ? VISIBLE : BLOCKED;
            }
            slot = (slot + 1) & this.mask;
        }
        return UNKNOWN;
    }

    // Records the answer of a point and grows the table when it gets half full.
    public void put(int x, int y, int z, boolean visible) {
        if ((this.size + 1) * 2 > this.keys.length) {
            if (this.keys.length >= MAX_CAPACITY) {
                return;
            }
            this.grow();
        }
        this.insert(key(x, y, z), visible);
    }

    // Stores a packed point without checking the load.
    private void insert(long key, boolean visible) {
        int slot = (int) HashCommon.mix(key) & this.mask;
        while (this.stamps[slot] == this.stamp) {
            if (this.keys[slot] == key) {
                this.answers[slot] = visible;
                return;
            }
            slot = (slot + 1) & this.mask;
        }
        this.stamps[slot] = this.stamp;
        this.keys[slot] = key;
        this.answers[slot] = visible;
        this.size++;
    }

    // Doubles the table and keeps the points of this pass.
    private void grow() {
        long[] oldKeys = this.keys;
        int[] oldStamps = this.stamps;
        boolean[] oldAnswers = this.answers;
        int live = this.stamp;
        this.allocate(oldKeys.length * 2);
        this.size = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldStamps[i] == live) {
                this.insert(oldKeys[i], oldAnswers[i]);
            }
        }
    }
}
