package com.bdmajora.impetus.engine.impl.util;

import com.bdmajora.impetus.engine.api.util.ColorARGB;

// Layout of the vertices of a baked quad.
public class ModelQuadUtil {
    // Indices of the vertex attributes in the integer array.
    public static final int POSITION_INDEX = 0,
            COLOR_INDEX = 3,
            TEXTURE_INDEX = 4,
            LIGHT_INDEX = 6,
            NORMAL_INDEX = 7;

    // Integers of one vertex.
    public static final int VERTEX_SIZE = 8;

    // Returns the index of a vertex in the array.
    public static int vertexOffset(int vertexIndex) {
        return vertexIndex * VERTEX_SIZE;
    }

    // Returns the brighter of the baked and calculated light with the emission limited to a byte.
    public static int mergeBakedLight(int packedLight, int vanillaLightEmission, int calcLight) {
        if (packedLight == 0 && vanillaLightEmission == 0)
            return calcLight;

        int psl = (packedLight >> 16) & 0xFF;
        int csl = (calcLight >> 16) & 0xFF;
        int pbl = (packedLight) & 0xFF;
        int cbl = (calcLight) & 0xFF;
        int bl = Math.max(Math.max(pbl, cbl), Math.min(255, Math.max(0, vanillaLightEmission)));
        int sl = Math.max(psl, csl);
        return (sl << 16) | bl;
    }

    // Returns the product of two colors and returns the other color when one is white.
    public static int mixARGBColors(int colorA, int colorB) {
        if (colorA == -1) {
            return colorB;
        } else if (colorB == -1) {
            return colorA;
        }
        int a = (int)((ColorARGB.unpackAlpha(colorA)/255.0f) * (ColorARGB.unpackAlpha(colorB)/255.0f) * 255.0f);
        int b = (int)((ColorARGB.unpackBlue(colorA)/255.0f) * (ColorARGB.unpackBlue(colorB)/255.0f) * 255.0f);
        int g = (int)((ColorARGB.unpackGreen(colorA)/255.0f) * (ColorARGB.unpackGreen(colorB)/255.0f) * 255.0f);
        int r = (int)((ColorARGB.unpackRed(colorA)/255.0f) * (ColorARGB.unpackRed(colorB)/255.0f) * 255.0f);
        return ColorARGB.pack(r, g, b, a);
    }
}
