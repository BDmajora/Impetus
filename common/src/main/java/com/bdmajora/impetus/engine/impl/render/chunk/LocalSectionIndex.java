package com.bdmajora.impetus.engine.impl.render.chunk;

public class LocalSectionIndex {
    // XZY order
    private static final int X_BITS = 0b111, X_OFFSET = 5;
    private static final int Y_BITS = 0b11, Y_OFFSET = 0;
    private static final int Z_BITS = 0b111, Z_OFFSET = 2;

    // Section-in-region coordinates to a local index
    public static int pack(int x, int y, int z) {
        return ((x & X_BITS) << X_OFFSET) | ((y & Y_BITS) << Y_OFFSET) | ((z & Z_BITS) << Z_OFFSET);
    }

    // Back to x
    public static int unpackX(int idx) {
        return (idx >> X_OFFSET) & X_BITS;
    }

    // Back to y
    public static int unpackY(int idx) {
        return (idx >> Y_OFFSET) & Y_BITS;
    }

    // Back to z
    public static int unpackZ(int idx) {
        return (idx >> Z_OFFSET) & Z_BITS;
    }
}