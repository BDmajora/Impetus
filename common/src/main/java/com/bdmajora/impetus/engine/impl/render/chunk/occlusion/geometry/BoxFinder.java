/*
 * Adapted from Canvas (https://github.com/vram-guild/canvas), Copyright 2019, 2020 grondag.
 * Licensed under the GNU Lesser General Public License v3.0.
 */

package com.bdmajora.impetus.engine.impl.render.chunk.occlusion.geometry;

import grondag.bitraster.PackedBox;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.Arrays;
import java.util.function.IntConsumer;

// Splits the opaque cells of a section into boxes that do not overlap.
public class BoxFinder {
    // Number of longs with one bit per cell of a section.
    public static final int INTERIOR_CACHE_WORDS = 64;

    // Number of longs of one slice.
    public static final int SLICE_WORD_COUNT = 4;

    // Boxes found by the last search.
    public final IntArrayList boxes = new IntArrayList();
    // Finder of rectangles in one slice.
    public final AreaFinder areaFinder;
    // Copy of the cells of the search.
    final long[] source = new long[INTERIOR_CACHE_WORDS];
    // Cells that boxes cover already.
    final long[] filled = new long[INTERIOR_CACHE_WORDS];
    // Slices that hold each rectangle.
    final int[] areaSlices = new int[Area.AREA_COUNT];
    // Candidate boxes ordered by volume.
    private final LongArrayList sortedBoxes = new LongArrayList();
    // Mask of the slice being marked.
    int mask;
    // Marks the slice of a rectangle.
    private final IntConsumer markSliceConsumer = areaIndex -> areaSlices[areaIndex] |= mask;
    // Number of cells not covered yet.
    private int voxelCount;

    // Creates a finder and rejects a null rectangle finder.
    public BoxFinder(AreaFinder areaFinder) {
        this.areaFinder = java.util.Objects.requireNonNull(areaFinder, "areaFinder");
    }

    // Finds the boxes of the cells at an offset of the array and rejects an array that is too short.
    public void findBoxes(long[] sourceBits, int sourceIndex) {
        java.util.Objects.checkFromIndexSize(sourceIndex, INTERIOR_CACHE_WORDS, sourceBits.length);
        Arrays.fill(filled, 0L);
        System.arraycopy(sourceBits, sourceIndex, source, 0, INTERIOR_CACHE_WORDS);
        boxes.clear();

        markSectionSlices();
        buildSortedSections();
        findSections();

        clearSectionBits();

        voxelCount = voxelCount(source, 0);

        if (voxelCount == 0) {
            return;
        }

        markBoxSlices();
        markBoxNeighborSlices();
        buildSortedBoxes();
        findDisjointBoxes();

        if (voxelCount > 0) {
            clearSectionBits();
            markBoxSlices();
            markBoxNeighborSlices();
            buildSortedBoxes();
            findDisjointBoxes();
        }
    }

    // Orders the boxes that span a full slice.
    private void buildSortedSections() {
        final LongArrayList sortedBoxes = this.sortedBoxes;
        sortedBoxes.clear();

        final int[] areaSlices = this.areaSlices;

        if (areaSlices[0] != 0) {
            // Handles the full plane
            addBoxesFromSlice(Area.indexToKey(0), areaSlices[0]);
        }

        for (int i = 1; i < Area.SECTION_COUNT; ++i) {
            final int areaIndex = Area.sectionToAreaIndex(i);
            final int slice = areaSlices[areaIndex];

            if (slice == 0xFFFF) {
                final int areaKey = Area.indexToKey(areaIndex);
                final int dy = (Area.y1(areaKey) - Area.y0(areaKey) + 1);
                final int dx = (Area.x1(areaKey) - Area.x0(areaKey) + 1);
                final long vol = (dx * dy * 16);
                sortedBoxes.add((vol << 34) | (areaIndex << 10) | (16 << 5) | 0);
            }
        }

        sortedBoxes.sort((a, b) -> Long.compare(b, a));
    }

    // Extends the slices of the rectangles into their neighbors.
    private void markBoxNeighborSlices() {
        final int[] areaSlices = this.areaSlices;

        for (int areaIndex = 0; areaIndex < Area.AREA_COUNT; ++areaIndex) {
            int slice = areaSlices[areaIndex];

            if (slice != 0) {
                if ((slice & 1) == 1 && (slice & 2) == 0) {
                    // Moves the first slice up
                    if (Area.isIncludedBySample(source, SLICE_WORD_COUNT, areaIndex)) {
                        slice |= 2;
                    }
                }

                int mask = 2;

                for (int z = 1; z < 15; z++) {
                    if ((slice & mask) != 0) {
                        // Moves to the lower slice
                        final int lowMask = (mask >> 1);

                        if ((slice & lowMask) == 0) {
                            if (Area.isIncludedBySample(source, (z - 1) * SLICE_WORD_COUNT, areaIndex)) {
                                slice |= lowMask;
                            }
                        }

                        // Moves to the upper slice
                        final int highMask = (mask << 1);

                        if ((slice & highMask) == 0) {
                            if (Area.isIncludedBySample(source, (z + 1) * SLICE_WORD_COUNT, areaIndex)) {
                                slice |= highMask;
                            }
                        }
                    }

                    mask <<= 1;
                }

                if ((slice & 0b1000000000000000) == 0b1000000000000000 && (slice & 0b0100000000000000) == 0) {
                    if (Area.isIncludedBySample(source, SLICE_WORD_COUNT * 14, areaIndex)) {
                        slice |= 0b0100000000000000;
                    }
                }

                areaSlices[areaIndex] = slice;
            }
        }
    }

    // Orders the candidate boxes.
    private void buildSortedBoxes() {
        final LongArrayList sortedBoxes = this.sortedBoxes;
        sortedBoxes.clear();

        final int[] areaSlices = this.areaSlices;

        for (int areaIndex = 0; areaIndex < Area.AREA_COUNT; ++areaIndex) {
            final int slice = areaSlices[areaIndex];

            if (slice != 0) {
                addBoxesFromSlice(Area.indexToKey(areaIndex), slice);
            }
        }

        sortedBoxes.sort((a, b) -> Long.compare(b, a));
    }

    // Adds a box for every run of slices.
    private void addBoxesFromSlice(int areaKey, int slice) {
        int z0 = -1;
        int mask = 1;
        final int x0 = Area.x0(areaKey);
        final int y0 = Area.y0(areaKey);
        final int x1 = Area.x1(areaKey);
        final int y1 = Area.y1(areaKey);
        final int areaIndex = Area.keyToIndex(areaKey);

        for (int z = 0; z < 16; z++) {
            if ((slice & mask) == 0) {
                // Ends the run
                if (z0 != -1) {
                    final int dz = (z - z0);
                    final int dy = (y1 - y0 + 1);
                    final int dx = (x1 - x0 + 1);
                    final long vol = dx * dy * dz;
                    sortedBoxes.add((vol << 34) | (areaIndex << 10) | (z << 5) | z0);
                    z0 = -1;
                }
            } else {
                // Starts the run
                if (z0 == -1) {
                    z0 = z;
                }
            }

            mask <<= 1;
        }

        // Ends the last run
        if (z0 != -1) {
            final int dz = (16 - z0);
            final int dy = (y1 - y0 + 1);
            final int dx = (x1 - x0 + 1);
            final long vol = dx * dy * dz;

            sortedBoxes.add((vol << 34) | (areaIndex << 10) | (16 << 5) | z0);
        }
    }

    // Marks the slices that hold each rectangle.
    private void markBoxSlices() {
        final long[] sourceBits = source;
        final AreaFinder areaFinder = this.areaFinder;
        final int[] areaSlices = this.areaSlices;
        Arrays.fill(areaSlices, 0);
        mask = 1;
        int sourceIndex = 0;

        for (int i = 0; i < 16; ++i) {
            areaFinder.find(sourceBits, sourceIndex, markSliceConsumer);
            sourceIndex += SLICE_WORD_COUNT;
            mask <<= 1;
        }
    }

    // Marks the slices that hold the section rectangles.
    private void markSectionSlices() {
        final long[] sourceBits = source;
        final AreaFinder areaFinder = this.areaFinder;
        final int[] areaSlices = this.areaSlices;
        Arrays.fill(areaSlices, 0);
        mask = 1;
        int sourceIndex = 0;

        for (int i = 0; i < 16; ++i) {
            areaFinder.findSections(sourceBits, sourceIndex, markSliceConsumer);
            sourceIndex += SLICE_WORD_COUNT;
            mask <<= 1;
        }
    }

    // Finds the boxes that span a full row or column.
    private void findSections() {
        final LongArrayList sortedBoxes = this.sortedBoxes;
        final int limit = sortedBoxes.size();
        final IntArrayList boxes = this.boxes;

        for (int i = 0; i < limit; i++) {
            final long box = sortedBoxes.getLong(i);
            final int areaIndex = (int) (box >> 10) & 0xFFFFFF;

            final int z0 = (int) box & 31;
            final int z1 = (int) (box >> 5) & 31;

            if (isAdditive(areaIndex, z0, z1)) {
                fill(areaIndex, z0, z1);
                final int areaKey = Area.indexToKey(areaIndex);
                boxes.add(PackedBox.pack(Area.x0(areaKey), Area.y0(areaKey), z0, Area.x1(areaKey) + 1, Area.y1(areaKey) + 1, z1, PackedBox.RANGE_EXTREME));
            }
        }
    }

    // Chooses boxes that do not overlap.
    private void findDisjointBoxes() {
        final LongArrayList sortedBoxes = this.sortedBoxes;
        final int limit = sortedBoxes.size();
        final IntArrayList boxes = this.boxes;

        for (int i = 0; i < limit; i++) {
            final long box = sortedBoxes.getLong(i);
            final int areaIndex = (int) (box >> 10) & 0xFFFFFF;
            final int z0 = (int) box & 31;
            final int z1 = (int) (box >> 5) & 31;

            if (!intersects(areaIndex, z0, z1)) {
                fill(areaIndex, z0, z1);
                final int vol = (int) (box >>> 34);
                final int areaKey = Area.indexToKey(areaIndex);
                boxes.add(PackedBox.pack(Area.x0(areaKey), Area.y0(areaKey), z0, Area.x1(areaKey) + 1, Area.y1(areaKey) + 1, z1, rangeFromVolume(vol)));
                voxelCount -= vol;

                if (voxelCount == 0) {
                    break;
                }
            }
        }
    }

    // Returns the range class of a volume.
    private int rangeFromVolume(int maxArea) {
        return maxArea <= 64 ? PackedBox.RANGE_NEAR : maxArea > 512 ? PackedBox.RANGE_FAR : PackedBox.RANGE_MID;
    }

    // Returns the number of cells set in an array.
    private int voxelCount(long[] sourceBits, int sourceIndex) {
        int result = 0;
        final int limit = sourceIndex + INTERIOR_CACHE_WORDS;

        for (int i = sourceIndex; i < limit; ++i) {
            final long bits = sourceBits[i];
            result += Long.bitCount(bits);
        }

        return result;
    }

    // Marks the cells of a box as covered.
    private void fill(int areaIndex, int z0, int z1) {
        final long[] filled = this.filled;
        int index = z0 * SLICE_WORD_COUNT;
        final long[] bits = areaFinder.bitsFromIndex(areaIndex);

        for (int z = z0; z < z1; ++z) {
            filled[index] |= bits[0];
            filled[index + 1] |= bits[1];
            filled[index + 2] |= bits[2];
            filled[index + 3] |= bits[3];
            index += SLICE_WORD_COUNT;
        }
    }

    // Returns whether a box overlaps covered cells.
    private boolean intersects(int areaIndex, int z0, int z1) {
        final long[] filled = this.filled;
        int index = z0 * SLICE_WORD_COUNT;

        for (int z = z0; z < z1; ++z) {
            if (Area.intersectsWithSample(filled, index, areaIndex)) {
                return true;
            }

            index += SLICE_WORD_COUNT;
        }

        return false;
    }

    // Returns whether a box adds uncovered cells.
    private boolean isAdditive(int areaIndex, int z0, int z1) {
        final long[] filled = this.filled;
        int index = z0 * SLICE_WORD_COUNT;

        final long a0 = Area.bitsFromIndex(areaIndex, 0);
        final long a1 = Area.bitsFromIndex(areaIndex, 1);
        final long a2 = Area.bitsFromIndex(areaIndex, 2);
        final long a3 = Area.bitsFromIndex(areaIndex, 3);

        for (int z = z0; z < z1; ++z) {
            if (((~filled[index++] & a0) | (~filled[index++] & a1) | (~filled[index++] & a2) | (~filled[index++] & a3)) != 0) {
                return true;
            }
        }

        return false;
    }

    // Clears the cells of the section boxes.
    private void clearSectionBits() {
        for (int i = 0; i < INTERIOR_CACHE_WORDS; ++i) {
            source[i] &= ~filled[i];
        }
    }
}
