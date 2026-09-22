package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.BitSet;

// Computes a section's visibility graph, following Tommaso Checchi's writeup at https://tomcc.github.io/2014/08/31/visibility-1.html
public class SectionVisibilityBuilder {
    private static final int SECTION_AXIS_SIZE = 16;
    private static final int SECTION_AXIS_MASK = SECTION_AXIS_SIZE - 1;
    private static final int TOTAL_BLOCKS = SECTION_AXIS_SIZE * SECTION_AXIS_SIZE * SECTION_AXIS_SIZE;
    private static final int BLOCKS_ON_ONE_FACE = SECTION_AXIS_SIZE * SECTION_AXIS_SIZE;
    private static final int BITS_PER_AXIS = 4;
    private static final int X_SHIFT = 0;
    private static final int Z_SHIFT = BITS_PER_AXIS;
    private static final int Y_SHIFT = BITS_PER_AXIS * 2;

    // all indices touching the edge of a section, used as the starting points for the floodfill
    private static final int[] INDICES_TO_INITIATE_FLOODFILL = buildFloodfillIndices();

    private final BitSet blocks;

    // Indices of every boundary block, the flood-fill seeds
    private static int[] buildFloodfillIndices() {
        IntArrayList indicesList = new IntArrayList(TOTAL_BLOCKS - (SECTION_AXIS_SIZE - 2) * (SECTION_AXIS_SIZE - 2) * (SECTION_AXIS_SIZE - 2));
        for (int x = 0; x < SECTION_AXIS_SIZE; x++) {
            for(int z = 0; z < SECTION_AXIS_SIZE; z++) {
                for(int y = 0; y < SECTION_AXIS_SIZE; y++) {
                    if (x == 0 || x == (SECTION_AXIS_SIZE - 1) || y == 0 || y == (SECTION_AXIS_SIZE - 1) || z == 0 || z == (SECTION_AXIS_SIZE - 1)) {
                        indicesList.add(getIndex(x, y, z));
                    }
                }
            }
        }
        return indicesList.toIntArray();
    }

    public SectionVisibilityBuilder() {
        this.blocks = new BitSet(SECTION_AXIS_SIZE * SECTION_AXIS_SIZE * SECTION_AXIS_SIZE);
    }

    // Which faces can see which, as the packed encoding
    public long computeVisibilityEncoding() {
        int opaqueCount = blocks.cardinality();
        if (opaqueCount == TOTAL_BLOCKS) {
            // everything is opaque, so we can't see anything anywhere
            return VisibilityEncoding.NULL;
        } else if (opaqueCount < BLOCKS_ON_ONE_FACE) {
            // Not enough blocks set to fully cover even one face, so everything must be visible from everything
            return VisibilityEncoding.EVERYTHING;
        } else {
            return computeWithFloodFill();
        }
    }

    // Flags a block as blocking the flood
    public void markOpaque(int x, int y, int z) {
        this.blocks.set(getIndex(x & 15, y & 15, z & 15));
    }

    // Floods from each unvisited boundary block and records the faces it escapes through
    private long computeWithFloodFill() {
        long resultEncoding = 0;
        var blocks = this.blocks;
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();

        for (int i : INDICES_TO_INITIATE_FLOODFILL) {
            if (blocks.get(i)) {
                continue;
            }

            int escapedFaces = this.exploreFrom(queue, i);

            // Every escaped face sees every other escaped face (and itself)
            for (int dir = 0; dir < GraphDirection.COUNT; dir++) {
                if (!GraphDirectionSet.contains(escapedFaces, dir)) {
                    continue;
                }

                for (int dir2 = 0; dir2 < GraphDirection.COUNT; dir2++) {
                    if (GraphDirectionSet.contains(escapedFaces, dir2)) {
                        resultEncoding |= 1L << VisibilityEncoding.bit(dir, dir2);
                    }
                }
            }
        }

        return resultEncoding;
    }

    // One BFS through non-opaque blocks; returns the GraphDirectionSet of faces it escaped through
    private int exploreFrom(IntArrayFIFOQueue queue, int startIndex) {
        var blocks = this.blocks;
        int escapedFaces = GraphDirectionSet.NONE;

        queue.clear();
        queue.enqueue(startIndex);

        // Mark the start location as handled
        blocks.set(startIndex, true);

        while (!queue.isEmpty()) {
            int idx = queue.dequeueInt();

            for (int dir = 0; dir < GraphDirection.COUNT; dir++) {
                int neighborIdx = getNeighborIndex(idx, dir);

                if (neighborIdx < 0) {
                    // We moved out of the section, mark this as an escaping face
                    escapedFaces |= GraphDirectionSet.of(dir);
                } else if (!blocks.get(neighborIdx)) {
                    // We can move within the section in that direction; mark this location as handled
                    blocks.set(neighborIdx, true);
                    queue.enqueue(neighborIdx);
                }
            }
        }

        return escapedFaces;
    }

    // Neighbour index, or -1 off the edge
    private static int getNeighborIndex(int idx, int dir) {
        return switch (dir) {
            case GraphDirection.UP -> step(idx, Y_SHIFT, true);
            case GraphDirection.DOWN -> step(idx, Y_SHIFT, false);
            case GraphDirection.EAST -> step(idx, X_SHIFT, true);
            case GraphDirection.WEST -> step(idx, X_SHIFT, false);
            case GraphDirection.SOUTH -> step(idx, Z_SHIFT, true);
            case GraphDirection.NORTH -> step(idx, Z_SHIFT, false);
            default -> throw new IllegalArgumentException();
        };
    }

    // One block along an axis, or -1 when already at that edge
    private static int step(int idx, int shift, boolean positive) {
        int axis = (idx >> shift) & SECTION_AXIS_MASK;

        if (positive) {
            return axis == SECTION_AXIS_MASK ? -1 : idx + (1 << shift);
        }

        return axis == 0 ? -1 : idx - (1 << shift);
    }

    // Flat index into the 16x16x16 bitset
    private static int getIndex(int x, int y, int z) {
        return (y << Y_SHIFT) | (z << Z_SHIFT) | (x << X_SHIFT);
    }
}
