package com.bdmajora.impetus.engine.impl.util.color;

import com.bdmajora.impetus.engine.api.util.ColorARGB;

// Box blur of a color buffer.
public class BoxBlur {

    // Blurs a buffer with a scratch buffer and rejects a different size or a radius out of range.
    public static void blur(ColorBuffer buf, ColorBuffer tmp, int radius) {
        java.util.Objects.requireNonNull(buf, "buf");
        java.util.Objects.requireNonNull(tmp, "tmp");
        if (buf.width != tmp.width || buf.height != tmp.height) {
            throw new IllegalArgumentException("Color buffers must have same dimensions");
        }

        if (radius < 0 || radius > 32767) {
            throw new IllegalArgumentException("Radius out of range " + radius);
        }

        int[] data = buf.data;

        if (data.length == 0 || isHomogenous(data)) {
            return;
        }

        blurImpl(data, tmp.data, buf.width, buf.height, radius);
        blurImpl(tmp.data, data, buf.height, buf.width, radius);
    }

    // Blurs every row and writes the result transposed.
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

            red += red * radius;
            green += green * radius;
            blue += blue * radius;

            for (int x = 1; x <= radius; x++) {
                var color = src[srcRowOffset + Math.min(width - 1, x)];
                red += ColorARGB.unpackRed(color);
                green += ColorARGB.unpackGreen(color);
                blue += ColorARGB.unpackBlue(color);
            }

            for (int x = 0; x < width; x++) {
                dst[ColorBuffer.getIndex(y, x, height)] = averageRGB(red, green, blue, multiplier);

                {
                    var color = src[srcRowOffset + Math.max(0, x - radius)];

                    red -= ColorARGB.unpackRed(color);
                    green -= ColorARGB.unpackGreen(color);
                    blue -= ColorARGB.unpackBlue(color);
                }

                {
                    var color = src[srcRowOffset + Math.min(width - 1, x + radius + 1)];
                    red += ColorARGB.unpackRed(color);
                    green += ColorARGB.unpackGreen(color);
                    blue += ColorARGB.unpackBlue(color);
                }
            }
        }
    }

    // Returns a multiplier that replaces a division by a shift.
    private static int getAveragingMultiplier(int size) {
        return (int)Math.ceil((1L << 24) / (double) size);
    }

    // Returns the average color of sums with the multiplier.
    public static int averageRGB(int red, int green, int blue, int multiplier) {
        return 0xFF << 24 | ((red * multiplier) >>> 24) << 16 | ((green * multiplier) >>> 24) << 8 | ((blue * multiplier) >>> 24);
    }

    // Returns whether every pixel is equal.
    private static boolean isHomogenous(int[] array) {
        int first = array[0];

        for (int i = 1; i < array.length; i++) {
            if (array[i] != first) {
                return false;
            }
        }

        return true;
    }

    // Buffer of pixels.
    public static class ColorBuffer {
        // Pixels in row order.
        protected final int[] data;
        // Size in pixels.
        protected final int width, height;

        // Creates a buffer and rejects a negative size.
        public ColorBuffer(int width, int height) {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("Negative size " + width + " " + height);
            }
            this.data = new int[Math.multiplyExact(width, height)];
            this.width = width;
            this.height = height;
        }

        // Writes one pixel.
        public void set(int x, int y, int color) {
            this.data[getIndex(x, y, this.width)] = color;
        }

        // Reads one pixel.
        public int get(int x, int y) {
            return this.data[getIndex(x, y, this.width)];
        }

        // Returns the index of a pixel.
        public static int getIndex(int x, int y, int width) {
            return (y * width) + x;
        }
    }
}
