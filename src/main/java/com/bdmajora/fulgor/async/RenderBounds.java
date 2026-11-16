package com.bdmajora.fulgor.async;

// Packed box of blocks to rerender.
public final class RenderBounds {
    // Prevents instantiation
    private RenderBounds() {
    }

    // Packs the corners of a box into one long.
    public static long pack(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return (minX & 15) | ((minY & 15) << 4) | ((minZ & 15) << 8) | ((maxX & 15) << 12) | ((maxY & 15) << 16) | ((maxZ & 15) << 20);
    }

    // Returns the box that covers two boxes.
    public static long union(long a, long b) {
        return pack(Math.min(minX(a), minX(b)), Math.min(minY(a), minY(b)), Math.min(minZ(a), minZ(b)),
                Math.max(maxX(a), maxX(b)), Math.max(maxY(a), maxY(b)), Math.max(maxZ(a), maxZ(b)));
    }

    // Returns the lowest x of a box.
    public static int minX(long bounds) {
        return (int) bounds & 15;
    }

    // Returns the lowest y of a box.
    public static int minY(long bounds) {
        return (int) (bounds >>> 4) & 15;
    }

    // Returns the lowest z of a box.
    public static int minZ(long bounds) {
        return (int) (bounds >>> 8) & 15;
    }

    // Returns the highest x of a box.
    public static int maxX(long bounds) {
        return (int) (bounds >>> 12) & 15;
    }

    // Returns the highest y of a box.
    public static int maxY(long bounds) {
        return (int) (bounds >>> 16) & 15;
    }

    // Returns the highest z of a box.
    public static int maxZ(long bounds) {
        return (int) (bounds >>> 20) & 15;
    }
}
