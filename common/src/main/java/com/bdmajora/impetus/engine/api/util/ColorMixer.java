package com.bdmajora.impetus.engine.api.util;

public class ColorMixer {
    private static final int CHANNEL_MASK = 0x00FF00FF;

    // Mixes aColor towards bColor at ratio (0..1) with bitwise trickery instead of per-channel float math; channel order is preserved
    public static int mix(int aColor, int bColor, float ratio) {
        int aRatio = (int) (256 * ratio); // int(ratio)
        int bRatio = 256 - aRatio; // int(1.0 - ratio)

        // Mask and shift two components from each colour into a packed vector of 16-bit components with the high 8 bits zeroed
        int a1 = (aColor >> 0) & CHANNEL_MASK;
        int b1 = (bColor >> 0) & CHANNEL_MASK;
        int a2 = (aColor >> 8) & CHANNEL_MASK;
        int b2 = (bColor >> 8) & CHANNEL_MASK;

        // Multiply the packed 16-bit components by each mix factor and add; cannot overflow since both are in 0..255

        // Shift the high 8 bits of each 16-bit component down and mask, leaving a vector of packed 8-bit components with every other slot empty
        int c1 = (((a1 * aRatio) + (b1 * bRatio) + 0x00800080) >> 8) & CHANNEL_MASK;
        int c2 = (((a2 * aRatio) + (b2 * bRatio) + 0x00800080) >> 8) & CHANNEL_MASK;

        // Join the color components into the original order
        return ((c1 << 0) | (c2 << 8));
    }

    // Multiplies two packed 32-bit colours together; channel order does not matter and is preserved
    public static int mul(int a, int b) {
        // Multiply each 8-bit component pair into a 16-bit intermediate, then shift the high half back down to 8 bits
        int c0 = ((((a >>  0) & 0xFF) * ((b >>  0) & 0xFF)) + 0xFF) >> 8;
        int c1 = ((((a >>  8) & 0xFF) * ((b >>  8) & 0xFF)) + 0xFF) >> 8;
        int c2 = ((((a >> 16) & 0xFF) * ((b >> 16) & 0xFF)) + 0xFF) >> 8;
        int c3 = ((((a >> 24) & 0xFF) * ((b >> 24) & 0xFF)) + 0xFF) >> 8;

        // Pack the components
        return (c0 <<  0) | (c1 <<  8) | (c2 << 16) | (c3 << 24);
    }

    // Multiplies the RGB channels of packed colour a by the single 8-bit component b; alpha must be in the top 8 bits
    public static int mulSingleWithoutAlpha(int a, int b) {
        b &= 0xFF; // Mask the component for safety
        // Multiply each 8-bit component by b into a 16-bit intermediate, then shift the high half back down to 8 bits
        int c0 = ((((a >>  0) & 0xFF) * b) + 0xFF) >> 8;
        int c1 = ((((a >>  8) & 0xFF) * b) + 0xFF) >> 8;
        int c2 = ((((a >> 16) & 0xFF) * b) + 0xFF) >> 8;
        int c3 = (a >> 24) & 0xFF;

        // Pack the components
        return (c0 <<  0) | (c1 <<  8) | (c2 << 16) | (c3 << 24);
    }
}
