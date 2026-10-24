package com.bdmajora.impetus.engine.api.util;

// Blends and multiplies packed colours two 8 bit channels at a time in any channel order
public final class ColorMixer {
    // Mask that keeps bytes 0 and 2 of a word
    private static final int EVEN_LANES = 0x00FF00FF;
    // Mask that keeps bytes 1 and 3 of a word
    private static final int ODD_LANES = 0xFF00FF00;
    // Half of one 8 bit step in each 16 bit lane used to round to nearest
    private static final int LANE_HALF = 0x00800080;
    // Keeps the alpha byte of a packed colour
    private static final int ALPHA_MASK = 0xFF000000;
    // Full blend weight of 1.0 in 8.8 fixed point
    private static final int WEIGHT_ONE = 256;

    // Prevents instantiation
    private ColorMixer() {
    }

    // Blends aColor at ratio with bColor at one minus ratio per channel rounded to nearest and clamps ratio between 0 and 1
    public static int mix(int aColor, int bColor, float ratio) {
        // Converts the ratio to a weight between 0 and 256 with NaN giving 0
        int weight = Math.max(0, Math.min(Math.round(ratio * WEIGHT_ONE), WEIGHT_ONE));
        int bEven = bColor & EVEN_LANES;
        int bOdd = (bColor >>> 8) & EVEN_LANES;
        // Computes b times 256 plus a minus b times the weight for each pair of lanes
        int even = (bEven << 8) + ((aColor & EVEN_LANES) - bEven) * weight + LANE_HALF;
        int odd = (bColor & ODD_LANES) + (((aColor >>> 8) & EVEN_LANES) - bOdd) * weight + LANE_HALF;
        // Takes the high byte of each lane as the rounded result
        return ((even >>> 8) & EVEN_LANES) | (odd & ODD_LANES);
    }

    // Multiplies two packed colours channel by channel as fractions of 255 rounded to nearest
    public static int mul(int a, int b) {
        int aOdd = a >>> 8;
        // Computes the four channel products in two words with two products per word
        int even = (a & 0xFF) * (b & 0xFF) + (a & 0x00FF0000) * ((b >>> 16) & 0xFF);
        int odd = (aOdd & 0xFF) * ((b >>> 8) & 0xFF) + (aOdd & 0x00FF0000) * (b >>> 24);
        return ((divide255(even) >>> 8) & EVEN_LANES) | (divide255(odd) & ODD_LANES);
    }

    // Multiplies the red and green and blue of a packed colour by a factor out of 255 rounded to nearest and keeps the alpha
    public static int mulSingleWithoutAlpha(int a, int b) {
        // Clamps the factor between 0 and 255
        int factor = Math.max(0, Math.min(b, 255));
        int even = (a & EVEN_LANES) * factor;
        int green = ((a >>> 8) & 0xFF) * factor;
        return (a & ALPHA_MASK) | ((divide255(even) >>> 8) & EVEN_LANES) | (divide255(green) & 0x0000FF00);
    }

    // Returns each 16 bit lane with its product divided by 255 and rounded in the high byte
    private static int divide255(int products) {
        int t = products + LANE_HALF;
        return t + ((t >>> 8) & EVEN_LANES);
    }
}