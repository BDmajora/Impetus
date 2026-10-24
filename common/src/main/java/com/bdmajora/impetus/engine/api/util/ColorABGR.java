package com.bdmajora.impetus.engine.api.util;

// Packs and unpacks ABGR colours with alpha in the high byte and red in the low byte
public final class ColorABGR implements ColorU8 {
    // Bit offsets of the green and blue and alpha components
    private static final int GREEN_COMPONENT_OFFSET = 8;
    private static final int BLUE_COMPONENT_OFFSET = 16;
    private static final int ALPHA_COMPONENT_OFFSET = 24;
    // Mask that keeps the red and green and blue bytes of a packed colour
    private static final int RGB_MASK = ~(COMPONENT_MASK << ALPHA_COMPONENT_OFFSET);

    // Prevents instantiation
    private ColorABGR() {
    }

    // Packs normalised red and green and blue components into an opaque ABGR colour
    public static int pack(float r, float g, float b) {
        return pack(ColorU8.normalizedFloatToByte(r),
                ColorU8.normalizedFloatToByte(g),
                ColorU8.normalizedFloatToByte(b),
                COMPONENT_MASK);
    }

    // Packs the low byte of each component into an ABGR colour
    public static int pack(int r, int g, int b, int a) {
        return (a << ALPHA_COMPONENT_OFFSET) |
                ((b & COMPONENT_MASK) << BLUE_COMPONENT_OFFSET) |
                ((g & COMPONENT_MASK) << GREEN_COMPONENT_OFFSET) |
                (r & COMPONENT_MASK);
    }

    // Replaces the alpha of a packed colour with a normalised alpha clamped between 0 and 1
    public static int withAlpha(int rgb, float alpha) {
        return withAlpha(rgb, ColorU8.normalizedFloatToByte(alpha));
    }

    // Replaces the alpha of a packed colour with the low byte of the given alpha
    public static int withAlpha(int rgb, int alpha) {
        return (alpha << ALPHA_COMPONENT_OFFSET) | (rgb & RGB_MASK);
    }

    // Packs normalised components clamped between 0 and 1 into an ABGR colour
    public static int pack(float r, float g, float b, float a) {
        return pack(ColorU8.normalizedFloatToByte(r),
                ColorU8.normalizedFloatToByte(g),
                ColorU8.normalizedFloatToByte(b),
                ColorU8.normalizedFloatToByte(a));
    }

    // Returns the red component of a packed ABGR colour between 0 and 255
    public static int unpackRed(int color) {
        return color & COMPONENT_MASK;
    }

    // Returns the green component of a packed ABGR colour between 0 and 255
    public static int unpackGreen(int color) {
        return (color >> GREEN_COMPONENT_OFFSET) & COMPONENT_MASK;
    }

    // Returns the blue component of a packed ABGR colour between 0 and 255
    public static int unpackBlue(int color) {
        return (color >> BLUE_COMPONENT_OFFSET) & COMPONENT_MASK;
    }

    // Returns the alpha component of a packed ABGR colour between 0 and 255
    public static int unpackAlpha(int color) {
        return color >>> ALPHA_COMPONENT_OFFSET;
    }
}