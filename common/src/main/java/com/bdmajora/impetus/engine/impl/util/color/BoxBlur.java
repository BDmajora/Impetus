package com.bdmajora.impetus.engine.impl.util.color;

import com.bdmajora.impetus.engine.api.util.ColorARGB;

public class BoxBlur {

    // Two-pass separable box blur; skipped entirely for a uniform buffer
    public static void blur(ColorBuffer buf, ColorBuffer tmp, int radius) {
        if (buf.width != tmp.width || buf.height != tmp.height) {
            throw new IllegalArgumentException("Color buffers must have same dimensions");
        }

        int[] data = buf.data;

        if (isHomogenous(data)) {
            return;
        }

        // Each pass transposes, so two passes blur both axes and land back in the original orientation
        blurImpl(data, tmp.data, buf.width, buf.height, radius);
        blurImpl(tmp.data, data, buf.width, buf.height, radius);
    }

    // One horizontal pass with a running sum
    private static void blurImpl(int[] src, int[] dst, int width, int height, int radius) {
        int multiplier = getAveragingMultiplier((radius * 2) + 1);

        for (int y = 0; y < height; y++) {
            int srcRowOffset = ColorBuffer.getIndex(0, y, width);

            int red, green, blue;

            {
                int color = src[srcRowOffset];
                red = ColorARGB.unpackRed(color);
                green = ColorARGB.unpackGreen(color);
                blue = ColorARGB.unpackBlue(color);
            }

            // Extend the window backwards by repeating the colors at the edge N times
            red += red * radius;
            green += green * radius;
            blue += blue * radius;

            // Extend the window forwards by sampling ahead N times
            for (int x = 1; x <= radius; x++) {
                var color = src[srcRowOffset + x];
                red += ColorARGB.unpackRed(color);
                green += ColorARGB.unpackGreen(color);
                blue += ColorARGB.unpackBlue(color);
            }

            for (int x = 0; x < width; x++) {
                // x and y are transposed to flip the output image (noinspection SuspiciousNameCombination)
                dst[ColorBuffer.getIndex(y, x, width)] = averageRGB(red, green, blue, multiplier);

                {
                    // Remove the color values that are behind the window
                    var color = src[srcRowOffset + Math.max(0, x - radius)];

                    red -= ColorARGB.unpackRed(color);
                    green -= ColorARGB.unpackGreen(color);
                    blue -= ColorARGB.unpackBlue(color);
                }

                {
                    // Add the color values that are ahead of the window
                    var color = src[srcRowOffset + Math.min(width - 1, x + radius + 1)];
                    red += ColorARGB.unpackRed(color);
                    green += ColorARGB.unpackGreen(color);
                    blue += ColorARGB.unpackBlue(color);
                }
            }
        }
    }

    // Pre-computes a multiplier so averaging can be done with a shift instead of a division (credit: 2No2Name)
    private static int getAveragingMultiplier(int size) {
        return (int)Math.ceil((1L << 24) / (double) size);
    }

    // Averages using the multiplier from getAveragingMultiplier() for the matching window size
    public static int averageRGB(int red, int green, int blue, int multiplier) {
        // Alpha is constant (fully opaque)
        return 0xFF << 24 | ((red * multiplier) >>> 24) << 16 | ((green * multiplier) >>> 24) << 8 | ((blue * multiplier) >>> 24);
    }

    // Every pixel the same, so blurring would change nothing
    private static boolean isHomogenous(int[] array) {
        int first = array[0];

        for (int i = 1; i < array.length; i++) {
            if (array[i] != first) {
                return false;
            }
        }

        return true;
    }

    public static class ColorBuffer {
        protected final int[] data;
        protected final int width, height;

        public ColorBuffer(int width, int height) {
            this.data = new int[width * height];
            this.width = width;
            this.height = height;
        }

        // Writes one pixel
        public void set(int x, int y, int color) {
            this.data[getIndex(x, y, this.width)] = color;
        }


        // Reads one pixel
        public int get(int x, int y) {
            return this.data[getIndex(x, y, this.width)];
        }

        // Row-major index
        public static int getIndex(int x, int y, int width) {
            return (y * width) + x;
        }
    }
}
