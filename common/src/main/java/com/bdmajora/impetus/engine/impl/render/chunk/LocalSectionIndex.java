package com.bdmajora.impetus.engine.impl.render.chunk;

// Index of a section inside its region.
public class LocalSectionIndex {
    // Mask and shift of x.
    private static final int X_BITS = 0b111, X_OFFSET = 5;
    // Mask and shift of y.
    private static final int Y_BITS = 0b11, Y_OFFSET = 0;
    // Mask and shift of z.
    private static final int Z_BITS = 0b111, Z_OFFSET = 2;

    // Packs the coordinates inside a region into an index.
    public static int pack(int x, int y, int z) {
        return ((x & X_BITS) << X_OFFSET) | ((y & Y_BITS) << Y_OFFSET) | ((z & Z_BITS) << Z_OFFSET);
    }

    // Returns the x of an index.
    public static int unpackX(int idx) {
        return (idx >> X_OFFSET) & X_BITS;
    }

    // Returns the y of an index.
    public static int unpackY(int idx) {
        return (idx >> Y_OFFSET) & Y_BITS;
    }

    // Returns the z of an index.
    public static int unpackZ(int idx) {
        return (idx >> Z_OFFSET) & Z_BITS;
    }
}