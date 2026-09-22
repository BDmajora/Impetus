package com.bdmajora.impetus.engine.impl.util;

import com.bdmajora.impetus.engine.api.util.ColorARGB;

// Vanilla's baked-quad vertex format, int[8] per vertex: [0-2] position floats, [3] ARGB color, [4-5] block UV floats, [6] light UV shorts, [7] normal bytes + padding
public class ModelQuadUtil {
    // Integer indices for vertex attributes, useful for accessing baked quad data
    public static final int POSITION_INDEX = 0,
            COLOR_INDEX = 3,
            TEXTURE_INDEX = 4,
            LIGHT_INDEX = 6,
            NORMAL_INDEX = 7;

    // Size of vertex format in 4-byte integers
    public static final int VERTEX_SIZE = 8;

    // Index into the flat vertex array for a vertex
    public static int vertexOffset(int vertexIndex) {
        return vertexIndex * VERTEX_SIZE;
    }

    // Combines baked light with vanilla emission, taking the brighter per channel
    public static int mergeBakedLight(int packedLight, int vanillaLightEmission, int calcLight) {
        // bail early in most cases
        if (packedLight == 0 && vanillaLightEmission == 0)
            return calcLight;

        int psl = (packedLight >> 16) & 0xFF;
        int csl = (calcLight >> 16) & 0xFF;
        int pbl = (packedLight) & 0xFF;
        int cbl = (calcLight) & 0xFF;
        int bl = Math.max(Math.max(pbl, cbl), vanillaLightEmission);
        // Emission raises BLOCK light only, never sky light (folding it into sky reported torches as open to the sky); latent while getVanillaLightEmission() defaults to 0
        int sl = Math.max(psl, csl);
        return (sl << 16) | bl;
    }

    // Mixes two ARGB colors like Forge's VertexConsumer does; bails early for the common single-source case
    public static int mixARGBColors(int colorA, int colorB) {
        // Most common case: Either quad coloring or tint-based coloring, but not both
        if (colorA == -1) {
            return colorB;
        } else if (colorB == -1) {
            return colorA;
        }
        // General case (rare): Both colorings, actually perform the multiplication
        int a = (int)((ColorARGB.unpackAlpha(colorA)/255.0f) * (ColorARGB.unpackAlpha(colorB)/255.0f) * 255.0f);
        int b = (int)((ColorARGB.unpackBlue(colorA)/255.0f) * (ColorARGB.unpackBlue(colorB)/255.0f) * 255.0f);
        int g = (int)((ColorARGB.unpackGreen(colorA)/255.0f) * (ColorARGB.unpackGreen(colorB)/255.0f) * 255.0f);
        int r = (int)((ColorARGB.unpackRed(colorA)/255.0f) * (ColorARGB.unpackRed(colorB)/255.0f) * 255.0f);
        return ColorARGB.pack(r, g, b, a);
    }
}
