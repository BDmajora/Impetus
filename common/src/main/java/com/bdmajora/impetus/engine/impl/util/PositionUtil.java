package com.bdmajora.impetus.engine.impl.util;

import com.bdmajora.impetus.engine.impl.common.util.MathUtil;

public class PositionUtil {

    // MC's range is +-30,000,000; 26 bits covers [-33554432, 33554431]
    private static final int SIZE_BITS_X = 26;
    private static final int SIZE_BITS_Z = SIZE_BITS_X;
    // MC's range is [0, 255]; the 12 leftover bits cover [-2048, 2047]
    private static final int SIZE_BITS_Y = 64 - SIZE_BITS_X - SIZE_BITS_Z;

    private static final long BITS_X = (1L << SIZE_BITS_X) - 1L;
    private static final long BITS_Y = (1L << SIZE_BITS_Y) - 1L;
    private static final long BITS_Z = (1L << SIZE_BITS_Z) - 1L;

    private static final int BIT_SHIFT_X = SIZE_BITS_Y + SIZE_BITS_Z;
    private static final int BIT_SHIFT_Z = SIZE_BITS_Y;
    private static final int BIT_SHIFT_Y = 0;

    // Block position into one long, same layout as vanilla's BlockPos.toLong
    public static long packBlock(int x, int y, int z) {
        return ((long) x & BITS_X) << BIT_SHIFT_X | ((long) y & BITS_Y) << BIT_SHIFT_Y | ((long) z & BITS_Z) << BIT_SHIFT_Z;
    }

    // Sign-extended x
    public static int unpackBlockX(long packed) {
        return (int) (packed << 64 - BIT_SHIFT_X - SIZE_BITS_X >> 64 - SIZE_BITS_X);
    }

    // y
    public static int unpackBlockY(long packed) {
        return (int) (packed << 64 - SIZE_BITS_Y >> 64 - SIZE_BITS_Y);
    }

    // Sign-extended z
    public static int unpackBlockZ(long packed) {
        return (int) (packed << 64 - BIT_SHIFT_Z - SIZE_BITS_Z >> 64 - SIZE_BITS_Z);
    }

    private static final long MAX_UNSIGNED_32BIT_INT = 4294967295L;
    private static final long SECTION_XZ_MASK = 4194303L;
    private static final long SECTION_Y_MASK = 1048575L;

    // Chunk column into one long, same layout as vanilla's ChunkPos.asLong
    public static long packChunk(int x, int z) {
        return (((long)z & MAX_UNSIGNED_32BIT_INT) << 32L) | ((long)x & MAX_UNSIGNED_32BIT_INT);
    }

    // Low 32 bits
    public static int unpackChunkX(long key) {
        return (int)(key & MAX_UNSIGNED_32BIT_INT);
    }

    // High 32 bits
    public static int unpackChunkZ(long key) {
        return (int)((key >>> 32) & MAX_UNSIGNED_32BIT_INT);
    }

    // Section position into one long
    public static long packSection(int x, int y, int z) {
        return (((long)x & SECTION_XZ_MASK) << 42L) | ((long)y & SECTION_Y_MASK) | (((long)z & SECTION_XZ_MASK) << 20L);
    }

    // Sign-extended x from packSection's top 22 bits
    public static int unpackSectionX(long key) {
        return (int) (key >> 42);
    }

    // Sign-extended y from the low 20 bits
    public static int unpackSectionY(long key) {
        return (int) (key << 44 >> 44);
    }

    // Sign-extended z from the middle 22 bits
    public static int unpackSectionZ(long key) {
        return (int) (key << 22 >> 42);
    }

    // World coordinate to section index
    public static int posToSectionCoord(double coord) {
        return posToSectionCoord(MathUtil.mojfloor(coord));
    }

    // Block coordinate to section index
    public static int posToSectionCoord(int coord) {
        return coord >> 4;
    }

    // Section index plus local offset to block coordinate
    public static int sectionToBlockCoord(int sec, int block) {
        return (sec << 4) + block;
    }
}
