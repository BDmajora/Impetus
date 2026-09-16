package com.bdmajora.fulgor.async;

// Changed-block bounds within one section packed as six nibbles (minX | minY << 4 | minZ << 8 | maxX << 12 | maxY << 16 | maxZ << 20); vanilla already inflates a render mark by one block, so a tight range rebuilds one render chunk where a whole-section mark rebuilds twenty-seven
public final class RenderBounds {
    private RenderBounds() {
    }

    public static long pack(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return minX | (minY << 4) | (minZ << 8) | (maxX << 12) | (maxY << 16) | (maxZ << 20);
    }

    public static long union(long a, long b) {
        return pack(Math.min(minX(a), minX(b)), Math.min(minY(a), minY(b)), Math.min(minZ(a), minZ(b)),
                Math.max(maxX(a), maxX(b)), Math.max(maxY(a), maxY(b)), Math.max(maxZ(a), maxZ(b)));
    }

    public static int minX(long bounds) {
        return (int) bounds & 15;
    }

    public static int minY(long bounds) {
        return (int) (bounds >>> 4) & 15;
    }

    public static int minZ(long bounds) {
        return (int) (bounds >>> 8) & 15;
    }

    public static int maxX(long bounds) {
        return (int) (bounds >>> 12) & 15;
    }

    public static int maxY(long bounds) {
        return (int) (bounds >>> 16) & 15;
    }

    public static int maxZ(long bounds) {
        return (int) (bounds >>> 20) & 15;
    }
}
