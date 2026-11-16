package com.bdmajora.impetus.engine.impl.texture;

import com.bdmajora.impetus.engine.api.util.ColorARGB;
import com.bdmajora.impetus.engine.impl.util.color.ColorSRGB;

// Averages pixels for mipmaps in linear space weighted by alpha.
public class MipmapHelper {
    // Returns the average of two pixels weighted by their alpha.
    public static int weightedAverageColor(int one, int two) {
        int alphaOne = ColorARGB.unpackAlpha(one);
        int alphaTwo = ColorARGB.unpackAlpha(two);

        if (alphaOne == alphaTwo) {
            return averageRgb(one, two, alphaOne);
        }

        if (alphaOne == 0) {
            return (two & 0x00FFFFFF) | ((alphaTwo >> 2) << 24);
        }

        if (alphaTwo == 0) {
            return (one & 0x00FFFFFF) | ((alphaOne >> 2) << 24);
        }

        float scale = 1.0f / (alphaOne + alphaTwo);
        return blendLinear(one, two, alphaOne * scale, alphaTwo * scale, (alphaOne + alphaTwo) >> 1);
    }

    // Returns the unweighted average of two colors in linear space.
    private static int averageRgb(int a, int b, int alpha) {
        return blendLinear(a, b, 0.5f, 0.5f, alpha);
    }

    // Returns two colors blended in linear space with a weight each.
    private static int blendLinear(int one, int two, float weightOne, float weightTwo, int alpha) {
        float r = ColorSRGB.srgbToLinear(ColorARGB.unpackRed(one)) * weightOne + ColorSRGB.srgbToLinear(ColorARGB.unpackRed(two)) * weightTwo;
        float g = ColorSRGB.srgbToLinear(ColorARGB.unpackGreen(one)) * weightOne + ColorSRGB.srgbToLinear(ColorARGB.unpackGreen(two)) * weightTwo;
        float b = ColorSRGB.srgbToLinear(ColorARGB.unpackBlue(one)) * weightOne + ColorSRGB.srgbToLinear(ColorARGB.unpackBlue(two)) * weightTwo;

        return ColorSRGB.linearToSrgb(r, g, b, alpha);
    }
}
