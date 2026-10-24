package com.bdmajora.impetus.engine.api.util;

// Packs and unpacks ARGB colours with alpha in the high byte and blue in the low byte
public final class ColorARGB implements ColorU8 {
    // Bit offsets of the alpha and red and green components
    private static final int ALPHA_COMPONENT_OFFSET = 24;
    private static final int RED_COMPONENT_OFFSET = 16;
    private static final int GREEN_COMPONENT_OFFSET = 8;
    // Mask that keeps the red and green and blue bytes of a packed colour
    private static final int RGB_MASK = ~(COMPONENT_MASK << ALPHA_COMPONENT_OFFSET);

    // Prevents instantiation
    private ColorARGB() {
    }

    // Packs the low byte of each component into an ARGB colour
    public static int pack(int r, int g, int b, int a) {
        return (a << ALPHA_COMPONENT_OFFSET) |
                ((r & COMPONENT_MASK) << RED_COMPONENT_OFFSET) |
                ((g & COMPONENT_MASK) << GREEN_COMPONENT_OFFSET) |
                (b & COMPONENT_MASK);
    }

    // Packs the low byte of each component into an opaque ARGB colour
    public static int pack(int r, int g, int b) {
        return pack(r, g, b, COMPONENT_MASK);
    }

    // Returns the alpha component of a packed ARGB colour between 0 and 255
    public static int unpackAlpha(int color) {
        return color >>> ALPHA_COMPONENT_OFFSET;
    }

    // Returns the red component of a packed ARGB colour between 0 and 255
    public static int unpackRed(int color) {
        return (color >> RED_COMPONENT_OFFSET) & COMPONENT_MASK;
    }

    // Returns the green component of a packed ARGB colour between 0 and 255
    public static int unpackGreen(int color) {
        return (color >> GREEN_COMPONENT_OFFSET) & COMPONENT_MASK;
    }

    // Returns the blue component of a packed ARGB colour between 0 and 255
    public static int unpackBlue(int color) {
        return color & COMPONENT_MASK;
    }

    // Converts an ARGB colour to ABGR with the low byte of the given alpha
    public static int toABGR(int color, int alpha) {
        return Integer.reverseBytes((color << COMPONENT_BITS) | (alpha & COMPONENT_MASK));
    }

    // Swaps the red and blue bytes to convert between ARGB and ABGR
    public static int toABGR(int color) {
        return Integer.reverseBytes(Integer.rotateLeft(color, COMPONENT_BITS));
    }

    // Replaces the alpha of a packed colour with the low byte of the given alpha
    public static int withAlpha(int rgb, int alpha) {
        return (alpha << ALPHA_COMPONENT_OFFSET) | (rgb & RGB_MASK);
    }
}