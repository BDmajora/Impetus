package com.bdmajora.impetus.engine.impl.texture;

import com.bdmajora.impetus.engine.api.util.ColorARGB;
import com.bdmajora.impetus.engine.impl.util.color.ColorSRGB;

// Mipmap downsampling blending in linear space (OptiFine's sRGB blend loses brightness) and weighting by alpha (vanilla's flat average dark-edges cutouts); ported from Umbra's MixinMipmapGenerator
public class MipmapHelper {
    // Averages two ARGB pixels per channel in linear space, weighted by alpha
    public static int weightedAverageColor(int one, int two) {
        int alphaOne = ColorARGB.unpackAlpha(one);
        int alphaTwo = ColorARGB.unpackAlpha(two);

        // In the case where the alpha values of the same, we can get by with an unweighted average.
        if (alphaOne == alphaTwo) {
            return averageRgb(one, two, alphaOne);
        }

        // A fully transparent pixel is ignored and the other taken as-is; alpha is divided by 4 instead of 2 to compensate for not changing the colour
        if (alphaOne == 0) {
            return (two & 0x00FFFFFF) | ((alphaTwo >> 2) << 24);
        }

        if (alphaTwo == 0) {
            return (one & 0x00FFFFFF) | ((alphaOne >> 2) << 24);
        }

        // Use the alpha values to compute relative weights of each color, and average the alphas themselves
        float scale = 1.0f / (alphaOne + alphaTwo);
        return blendLinear(one, two, alphaOne * scale, alphaTwo * scale, (alphaOne + alphaTwo) >> 1);
    }

    // Non-weighted average of the two sRGB colors in linear space, avoiding brightness losses
    private static int averageRgb(int a, int b, int alpha) {
        return blendLinear(a, b, 0.5f, 0.5f, alpha);
    }

    // Converts both colours to linear space, weights and sums them, then packs back to sRGB with the given alpha
    private static int blendLinear(int one, int two, float weightOne, float weightTwo, int alpha) {
        float r = ColorSRGB.srgbToLinear(ColorARGB.unpackRed(one)) * weightOne + ColorSRGB.srgbToLinear(ColorARGB.unpackRed(two)) * weightTwo;
        float g = ColorSRGB.srgbToLinear(ColorARGB.unpackGreen(one)) * weightOne + ColorSRGB.srgbToLinear(ColorARGB.unpackGreen(two)) * weightTwo;
        float b = ColorSRGB.srgbToLinear(ColorARGB.unpackBlue(one)) * weightOne + ColorSRGB.srgbToLinear(ColorARGB.unpackBlue(two)) * weightTwo;

        return ColorSRGB.linearToSrgb(r, g, b, alpha);
    }
}
