package com.bdmajora.impetus.engine.api.util;

// Packs/unpacks ABGR integer colours (the layout OpenGL uses for colour vectors): alpha in bits 24-31, then blue, green, red in bits 0-7
public class ColorABGR implements ColorU8 {
    private static final int RED_COMPONENT_OFFSET = 0;
    private static final int GREEN_COMPONENT_OFFSET = 8;
    private static final int BLUE_COMPONENT_OFFSET = 16;
    private static final int ALPHA_COMPONENT_OFFSET = 24;

    // packs the colour components into ABGR format, with the alpha component fully opaque; 1.0f, not COMPONENT_MASK, since the float overload normalises its alpha too
    public static int pack(float r, float g, float b) {
        return pack(r, g, b, 1.0f);
    }

    // packs the colour components into ABGR format
    public static int pack(int r, int g, int b, int a) {
        return ((a & COMPONENT_MASK) << ALPHA_COMPONENT_OFFSET) |
                ((b & COMPONENT_MASK) << BLUE_COMPONENT_OFFSET) |
                ((g & COMPONENT_MASK) << GREEN_COMPONENT_OFFSET) |
                ((r & COMPONENT_MASK) << RED_COMPONENT_OFFSET);
    }

    // packs an rgb triple and a floating point alpha into ABGR format
    public static int withAlpha(int rgb, float alpha) {
        return withAlpha(rgb, ColorU8.normalizedFloatToByte(alpha));
    }

    // packs an rgb triple and an integer alpha into ABGR format
    public static int withAlpha(int rgb, int alpha) {
        return (alpha << ALPHA_COMPONENT_OFFSET) | (rgb & ~(COMPONENT_MASK << ALPHA_COMPONENT_OFFSET));
    }

    // Converts normalised float components into a packed ABGR integer via pack(int, int, int, int)
    public static int pack(float r, float g, float b, float a) {
        return pack(ColorU8.normalizedFloatToByte(r),
                ColorU8.normalizedFloatToByte(g),
                ColorU8.normalizedFloatToByte(b),
                ColorU8.normalizedFloatToByte(a));
    }

    // the red component of a packed 32-bit ABGR colour, in 0..255
    public static int unpackRed(int color) {
        return (color >> RED_COMPONENT_OFFSET) & COMPONENT_MASK;
    }

    // the green component of a packed 32-bit ABGR colour, in 0..255
    public static int unpackGreen(int color) {
        return (color >> GREEN_COMPONENT_OFFSET) & COMPONENT_MASK;
    }

    // the blue component of a packed 32-bit ABGR colour, in 0..255
    public static int unpackBlue(int color) {
        return (color >> BLUE_COMPONENT_OFFSET) & COMPONENT_MASK;
    }

    // the alpha component of a packed 32-bit ABGR colour, in 0..255
    public static int unpackAlpha(int color) {
        return (color >> ALPHA_COMPONENT_OFFSET) & COMPONENT_MASK;
    }
}
