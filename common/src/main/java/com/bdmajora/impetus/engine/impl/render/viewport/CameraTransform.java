package com.bdmajora.impetus.engine.impl.render.viewport;

import com.bdmajora.impetus.engine.impl.render.chunk.region.RenderRegion;

public final class CameraTransform {
    // Precision step of camera deltas.
    private static final float PRECISION_MODIFIER = RenderRegion.REGION_WIDTH * 16; // 16 blocks per section

    // The integer component of the translation vector
    public final int intX, intY, intZ;

    // The fractional component of the translation vector
    public final float fracX, fracY, fracZ;

    // The original transform.
    public final double x, y, z;

    public CameraTransform(double x, double y, double z) {
        this.intX = integral(x);
        this.intY = integral(y);
        this.intZ = integral(z);

        this.fracX = fractional(x);
        this.fracY = fractional(y);
        this.fracZ = fractional(z);

        this.x = x;
        this.y = y;
        this.z = z;
    }

    // Floor as int
    private static int integral(double value) {
        return (int) value;
    }

    // Returns the fractional part as a float.
    private static float fractional(double value) {
        float fullPrecision = (float) (value - integral(value));
        float modifier = Math.copySign(PRECISION_MODIFIER, fullPrecision);

        return  (fullPrecision + modifier) - modifier;
    }

    // By the split fields
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CameraTransform that = (CameraTransform) o;
        return x == that.x && y == that.y && z == that.z;
    }

    // By the split fields
    @Override
    public int hashCode() {
        int hash = 1;
        hash = 31 * hash + Double.hashCode(x);
        hash = 31 * hash + Double.hashCode(y);
        hash = 31 * hash + Double.hashCode(z);
        return hash;
    }
}
