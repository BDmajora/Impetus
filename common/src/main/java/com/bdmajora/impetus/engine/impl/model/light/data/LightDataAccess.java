package com.bdmajora.impetus.engine.impl.model.light.data;

import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;

import java.util.Arrays;

// Caches packed light and occlusion data of each block.
public abstract class LightDataAccess {
    private static final int NEIGHBOR_BLOCK_RADIUS = 2;
    private static final int BLOCK_LENGTH = 16 + (NEIGHBOR_BLOCK_RADIUS * 2);

    private final int[] light;

    private int xOffset, yOffset, zOffset;

    public LightDataAccess() {
        this.light = new int[BLOCK_LENGTH * BLOCK_LENGTH * BLOCK_LENGTH];
    }

    // Re-centres the cache on a section and clears it
    public void reset(int minBlockX, int minBlockY, int minBlockZ) {
        this.xOffset = minBlockX - NEIGHBOR_BLOCK_RADIUS;
        this.yOffset = minBlockY - NEIGHBOR_BLOCK_RADIUS;
        this.zOffset = minBlockZ - NEIGHBOR_BLOCK_RADIUS;

        Arrays.fill(this.light, 0);
    }

    // Flat index into the cache array
    private int index(int x, int y, int z) {
        int x2 = x - this.xOffset;
        int y2 = y - this.yOffset;
        int z2 = z - this.zOffset;

        return (z2 * BLOCK_LENGTH * BLOCK_LENGTH) + (y2 * BLOCK_LENGTH) + x2;
    }

    protected abstract int compute(int x, int y, int z);

    // Returns the packed light word of a position and computes it on first use.
    public int get(int x, int y, int z) {
        int l = this.index(x, y, z);

        int word = this.light[l];

        if (word != 0) {
            return word;
        }

        return this.light[l] = this.compute(x, y, z);
    }

    // Word for the block two steps away along d1 then d2
    public int get(int x, int y, int z, ModelQuadFacing d1, ModelQuadFacing d2) {
        return this.get(x + d1.getStepX() + d2.getStepX(),
                y + d1.getStepY() + d2.getStepY(),
                z + d1.getStepZ() + d2.getStepZ());
    }

    // Word for the neighbour along dir
    public int get(int x, int y, int z, ModelQuadFacing dir) {
        return this.get(x + dir.getStepX(),
                y + dir.getStepY(),
                z + dir.getStepZ());
    }

    // Bits 0..3
    public static int packBL(int blockLight) {
        return blockLight & 0xF;
    }

    // Bits 0..3
    public static int unpackBL(int word) {
        return word & 0xF;
    }

    // Bits 4..7
    public static int packSL(int skyLight) {
        return (skyLight & 0xF) << 4;
    }

    // Bits 4..7
    public static int unpackSL(int word) {
        return (word >>> 4) & 0xF;
    }

    // Bits 8..11
    public static int packLU(int luminance) {
        return (luminance & 0xF) << 8;
    }

    // Bits 8..11
    public static int unpackLU(int word) {
        return (word >>> 8) & 0xF;
    }

    // AO as a 12-bit fixed-point fraction
    public static int packAO(float ao) {
        int aoi = (int) (ao * 4096.0f);
        return (aoi & 0xFFFF) << 12;
    }

    // Back to a float
    public static float unpackAO(int word) {
        int aoi = (word >>> 12) & 0xFFFF;
        return aoi * (1.0f / 4096.0f);
    }

    // Emissive flag bit
    public static int packEM(boolean emissive) {
        return (emissive ? 1 : 0) << 28;
    }

    // Emissive flag bit
    public static boolean unpackEM(int word) {
        return ((word >>> 28) & 0b1) != 0;
    }

    // Opaque flag bit
    public static int packOP(boolean opaque) {
        return (opaque ? 1 : 0) << 29;
    }

    // Opaque flag bit
    public static boolean unpackOP(int word) {
        return ((word >>> 29) & 0b1) != 0;
    }

    // Packs the full opaque flag bit.
    public static int packFO(boolean opaque) {
        return (opaque ? 1 : 0) << 30;
    }

    // Full-opaque flag bit
    public static boolean unpackFO(int word) {
        return ((word >>> 30) & 0b1) != 0;
    }

    // Full-cube flag bit
    public static int packFC(boolean fullCube) {
        return (fullCube ? 1 : 0) << 31;
    }

    // Full-cube flag bit
    public static boolean unpackFC(int word) {
        return ((word >>> 31) & 0b1) != 0;
    }

    // Block and sky into vanilla's lightmap layout
    public static int pack(int block, int sky) {
        return block << 4 | sky << 20;
    }

    // Vanilla layout
    public static int unpackBlock(int packed) {
        return (packed & 0xFFFF) >> 4;
    }

    // Vanilla layout
    public static int unpackSky(int packed) {
        return (packed >> 20) & 0xFFFF;
    }

    // Returns the lightmap of a packed word.
    public static int getLightmap(int word) {
        return pack(Math.max(unpackBL(word), unpackLU(word)), unpackSL(word));
    }

    public static final int FULL_BRIGHT = pack(15, 15);

    // Returns full bright for emissive blocks and the lightmap otherwise.
    public static int getEmissiveLightmap(int word) {
        if (unpackEM(word)) {
            return FULL_BRIGHT;
        } else {
            return getLightmap(word);
        }
    }
}