/*
 * Ported from Canvas (https://github.com/vram-guild/canvas), Copyright 2019, 2020 grondag.
 * Licensed under the GNU Lesser General Public License v3.0.
 */

package com.bdmajora.impetus.engine.impl.render.chunk.occlusion.geometry;

import java.util.function.IntConsumer;

// Finds the largest rectangles of set cells in a sixteen by sixteen grid.
public class AreaFinder {
    // Working copy of the grid.
    final long[] bits = new long[4];

    // Returns the key of the largest rectangle of set cells and rejects a null or short array.
    public static int findLargest(long[] bitsIn) {
        if (bitsIn.length < 4) {
            throw new IllegalArgumentException("Grid needs four longs");
        }
        int bestX0 = 0;
        int bestY0 = 0;
        int bestX1 = -1;
        int bestY1 = -1;
        int bestArea = 0;

        // Heights of the previous row
        long heights = 0;

        for (int y = 0; y < 16; ++y) {
            final int rowBits = (int) ((bitsIn[y >> 2] >> ((y & 3) << 4)) & 0xFFFF);

            if (rowBits == 0) {
                heights = 0;
                continue;
            }

            // Tracks the start and height of open runs
            long stackX15 = 0;
            long stackH16 = 0;
            int stackSize = 0;

            // Calculates the height of the first column
            int runHeight = (rowBits & 1) == 0 ? 0 : (1 + getVal15(heights, 0));
            int runStart = 0;

            // Stores the height for the next row
            if (y != 15) heights = setVal15(heights, 0, runHeight);

            // Includes column sixteen to close the last run
            for (int x = 1; x <= 16; ++x) {
                // Calculates the height of this column
                final int h = (rowBits & (1 << x)) == 0 ? 0 : (1 + getVal15(heights, x));

                // Starts a new run when the column is higher
                if (h > runHeight) {
                    // Pushes the current run
                    if (runHeight != 0) {
                        stackX15 = setVal15(stackX15, stackSize, runStart);
                        stackH16 = setVal16(stackH16, stackSize, runHeight);
                        ++stackSize;
                    }

                    // Starts the run here
                    runStart = x;
                    runHeight = h;
                } else {
                    // Closes the runs that are higher than the column
                    while (h < runHeight) {
                        // Checks the area of the run
                        final int a = (x - runStart) * runHeight;

                        if (a > bestArea) {
                            bestArea = a;
                            bestX0 = runStart;
                            bestX1 = x - 1;
                            bestY0 = y - runHeight + 1;
                            bestY1 = y;
                        }

                        if (stackSize == 0) {
                            // Continues the run at the lower height
                            runHeight = h;
                        } else { // stackSize > 0
                            --stackSize;
                            final int stackStart = getVal15(stackX15, stackSize);
                            final int stackHeight = getVal16(stackH16, stackSize);

                            if (stackHeight == h) {
                                // Resumes the run of the stack
                                runHeight = h;
                                runStart = stackStart;
                            } else if (stackHeight < h) {
                                // Keeps the run on the stack
                                ++stackSize;
                                // Continues at the new height
                                runHeight = h;
                            } else {
                                // Closes the run of the stack next
                                runHeight = stackHeight;
                                runStart = stackStart;
                            }
                        }
                    }
                }

                // Stores the height of this column
                if (y != 15 && x < 16) heights = setVal15(heights, x, h);
            }
        }

        return Area.areaKey(bestX0, bestY0, bestX1, bestY1);
    }

    // Returns a value from one to sixteen of a packed position.
    private static int getVal16(long packed, int x) {
        return 1 + getVal15(packed, x);
    }

    // Stores a value from one to sixteen at a packed position.
    private static long setVal16(long packed, int x, int val) {
        return setVal15(packed, x, val - 1);
    }

    // Returns a value from zero to fifteen of a packed position.
    private static int getVal15(long packed, int x) {
        return (int) ((packed >>> (x << 2)) & 0xF);
    }

    // Stores a value from zero to fifteen at a packed position.
    private static long setVal15(long packed, int x, int val) {
        final int shift = x << 2;
        final long mask = 0xFL << shift;
        return (packed & ~mask) | (((long) val) << shift);
    }

    // Returns the cells of a rectangle in the shared working array.
    public long[] bitsFromIndex(int areaIndex) {
        final long[] result = bits;

        result[0] = Area.bitsFromIndex(areaIndex, 0);
        result[1] = Area.bitsFromIndex(areaIndex, 1);
        result[2] = Area.bitsFromIndex(areaIndex, 2);
        result[3] = Area.bitsFromIndex(areaIndex, 3);

        return result;
    }

    // Reports the largest rectangles until no cell is left.
    public void find(long[] bitsIn, int sourceIndex, IntConsumer areaIndexConsumer) {
        java.util.Objects.requireNonNull(areaIndexConsumer, "areaIndexConsumer");
        java.util.Objects.checkFromIndexSize(sourceIndex, 4, bitsIn.length);
        final long[] bits = this.bits;
        System.arraycopy(bitsIn, sourceIndex, bits, 0, 4);

        int bitCount = Long.bitCount(bits[0]) + Long.bitCount(bits[1]) + Long.bitCount(bits[2]) + Long.bitCount(bits[3]);

        while (bitCount > 0) {
            final int key = findLargest(bits);
            final int index = Area.keyToIndex(key);
            areaIndexConsumer.accept(index);
            Area.clearBits(bits, 0, index);
            bitCount -= Area.size(key);
        }
    }

    // Reports the section rectangles that the grid fully holds.
    public void findSections(long[] bitsIn, int sourceIndex, IntConsumer areaIndexConsumer) {
        java.util.Objects.requireNonNull(areaIndexConsumer, "areaIndexConsumer");
        java.util.Objects.checkFromIndexSize(sourceIndex, 4, bitsIn.length);
        final long[] bits = this.bits;
        System.arraycopy(bitsIn, sourceIndex, bits, 0, 4);

        final int bitCount = Long.bitCount(bits[0]) + Long.bitCount(bits[1]) + Long.bitCount(bits[2]) + Long.bitCount(bits[3]);

        if (bitCount == 0) {
            return;
        }

        for (int i = 0; i < Area.SECTION_COUNT; ++i) {
            final int areaIndex = Area.sectionToAreaIndex(i);

            if (Area.isIncludedBySample(bits, 0, areaIndex)) {
                areaIndexConsumer.accept(areaIndex);
            }
        }
    }
}
