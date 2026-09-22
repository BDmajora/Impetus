package com.bdmajora.impetus.engine.api.util;

import org.joml.Math;
import org.joml.Vector3f;

// Packed normal vectors, 8 bits per component in [-1.0, 1.0]: X in bits 16-23, Y in 8-15, Z in 0-7, top byte padding
public class NormI8 {
    private static final int X_COMPONENT_OFFSET = 0;
    private static final int Y_COMPONENT_OFFSET = 8;
    private static final int Z_COMPONENT_OFFSET = 16;
    // The padding byte, which a tangent uses for its handedness
    private static final int W_COMPONENT_OFFSET = 24;

    // the maximum value of a normal's vector component
    private static final float COMPONENT_RANGE = 127.0f;

    // Multiplier used instead of dividing by COMPONENT_RANGE; multiplication is slightly faster and this is a hot path
    private static final float NORM = 1.0f / COMPONENT_RANGE;

    // Vector form of pack
    public static int pack(Vector3f normal) {
        return pack(normal.x(), normal.y(), normal.z());
    }

    // Packs the components into a 32-bit integer in XYZ order with the padding byte at the end
    public static int pack(float x, float y, float z) {
        int normX = encode(x);
        int normY = encode(y);
        int normZ = encode(z);

        return (normZ << Z_COMPONENT_OFFSET) | (normY << Y_COMPONENT_OFFSET) | (normX << X_COMPONENT_OFFSET);
    }

    // Four-component form for tangents: xyz as a normal plus the handedness sign in the top byte, the same layout OptiFine's at_tangent expects
    public static int pack(float x, float y, float z, float w) {
        return pack(x, y, z) | (encode(w) << W_COMPONENT_OFFSET);
    }

    // encodes a float in -1.0..1.0 as a normalised unsigned integer in 0..255, ready for graphics memory
    private static int encode(float comp) {
        // TODO: is the clamp necessary here? our inputs should always be normalized vector components
        return ((int) (Math.clamp(-1.0F, 1.0F, comp) * COMPONENT_RANGE) & 255);
    }

    // unpacks the x component of the packed normal, denormalising it to a float in -1.0..1.0
    public static float unpackX(int norm) {
        return ((byte) ((norm >> X_COMPONENT_OFFSET) & 0xFF)) * NORM;
    }

    // unpacks the y component of the packed normal, denormalising it to a float in -1.0..1.0
    public static float unpackY(int norm) {
        return ((byte) ((norm >> Y_COMPONENT_OFFSET) & 0xFF)) * NORM;
    }

    // unpacks the z component of the packed normal, denormalising it to a float in -1.0..1.0
    public static float unpackZ(int norm) {
        return ((byte) ((norm >> Z_COMPONENT_OFFSET) & 0xFF)) * NORM;
    }

    // unpacks the fourth component, the tangent handedness
    public static float unpackW(int norm) {
        return ((byte) ((norm >> W_COMPONENT_OFFSET) & 0xFF)) * NORM;
    }
}
