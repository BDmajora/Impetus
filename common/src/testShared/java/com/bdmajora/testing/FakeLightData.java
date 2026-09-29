package com.bdmajora.testing;

import com.bdmajora.impetus.engine.impl.model.light.data.LightDataAccess;

import java.util.HashMap;
import java.util.Map;

// A light cache over a sparse map: air everywhere except the words a test plants, so every position still packs to a non-zero word
public final class FakeLightData extends LightDataAccess {
    private final Map<Long, Integer> words = new HashMap<>();
    public int computes;

    public FakeLightData() {
        reset(0, 0, 0);
    }

    private static long key(int x, int y, int z) {
        return ((long) x << 40) ^ ((long) (y & 0xFFFFF) << 20) ^ (z & 0xFFFFF);
    }

    // Plants a packed word at a position
    public FakeLightData put(int x, int y, int z, int word) {
        words.put(key(x, y, z), word);
        return this;
    }

    // A lit, non-opaque block: full sky, some block light, AO one
    public static int air(int blockLight) {
        return packBL(blockLight) | packSL(15) | packAO(1.0f);
    }

    // A full opaque cube
    public static int stone() {
        return packOP(true) | packFO(true) | packFC(true) | packAO(0.2f);
    }

    @Override
    protected int compute(int x, int y, int z) {
        computes++;
        return words.getOrDefault(key(x, y, z), air(0));
    }
}
