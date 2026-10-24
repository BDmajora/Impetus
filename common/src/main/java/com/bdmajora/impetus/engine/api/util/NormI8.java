package com.bdmajora.impetus.engine.api.util;

import org.joml.Vector3f;

// Packs and unpacks normal vectors as one signed byte per component with X in the low byte and the tangent handedness in the high byte
public final class NormI8 {
    // Bit offsets of the Y and Z and W components
    private static final int Y_COMPONENT_OFFSET = 8;
    private static final int Z_COMPONENT_OFFSET = 16;
    private static final int W_COMPONENT_OFFSET = 24;

    // Mask that keeps one encoded component
    private static final int COMPONENT_MASK = 0xFF;

    // Largest magnitude of an encoded component
    private static final int COMPONENT_RANGE = 127;

    // Reciprocal of the component range as a double
    private static final double COMPONENT_RANGE_INVERSE = 1.0 / COMPONENT_RANGE;

    // Prevents instantiation
    private NormI8() {
    }

    // Packs the components of the vector
    public static int pack(Vector3f normal) {
        return pack(normal.x(), normal.y(), normal.z());
    }

    // Packs the components into an integer in XYZ order with the high byte left zero
    public static int pack(float x, float y, float z) {
        return (encode(z) << Z_COMPONENT_OFFSET) | (encode(y) << Y_COMPONENT_OFFSET) | encode(x);
    }

    // Packs a tangent with its handedness sign in the high byte
    public static int pack(float x, float y, float z, float w) {
        return pack(x, y, z) | (encode(w) << W_COMPONENT_OFFSET);
    }

    // Rounds the component times 127 to the nearest integer and clamps it between minus 127 and 127 with NaN giving 0
    private static int encode(float comp) {
        return Math.max(-COMPONENT_RANGE, Math.min((int) Math.rint(comp * (double) COMPONENT_RANGE), COMPONENT_RANGE)) & COMPONENT_MASK;
    }

    // Converts a signed byte to a float and reads minus 128 as minus 1
    private static float decode(int component) {
        return (float) (Math.max(component, -COMPONENT_RANGE) * COMPONENT_RANGE_INVERSE);
    }

    // Returns the X component of a packed normal between minus 1 and 1
    public static float unpackX(int norm) {
        return decode((byte) norm);
    }

    // Returns the Y component of a packed normal between minus 1 and 1
    public static float unpackY(int norm) {
        return decode((byte) (norm >> Y_COMPONENT_OFFSET));
    }

    // Returns the Z component of a packed normal between minus 1 and 1
    public static float unpackZ(int norm) {
        return decode((byte) (norm >> Z_COMPONENT_OFFSET));
    }

    // Returns the tangent handedness from the high byte between minus 1 and 1
    public static float unpackW(int norm) {
        return decode(norm >> W_COMPONENT_OFFSET);
    }
}