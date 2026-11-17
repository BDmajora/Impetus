package com.bdmajora.impetus.engine.impl.render.chunk.occlusion;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.geometry.BoxFinder;
import com.bdmajora.impetus.engine.impl.render.chunk.occlusion.geometry.OccluderBoxes;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.BitSet;

// Computes which faces of a section see each other and the boxes that occlude other sections.
public class SectionVisibilityBuilder {
    // Cells along one axis.
    private static final int SECTION_AXIS_SIZE = 16;
    // Mask of a coordinate.
    private static final int SECTION_AXIS_MASK = SECTION_AXIS_SIZE - 1;
    // Number of cells.
    static final int TOTAL_BLOCKS = SECTION_AXIS_SIZE * SECTION_AXIS_SIZE * SECTION_AXIS_SIZE;
    // Cells on one face.
    private static final int BLOCKS_ON_ONE_FACE = SECTION_AXIS_SIZE * SECTION_AXIS_SIZE;
    // Bits of a coordinate.
    private static final int BITS_PER_AXIS = 4;
    // Shift of x in an index.
    private static final int X_SHIFT = 0;
    // Shift of z in an index.
    private static final int Z_SHIFT = BITS_PER_AXIS;
    // Shift of y in an index.
    private static final int Y_SHIFT = BITS_PER_AXIS * 2;

    // Cells on the surface where the fill starts.
    private static final int[] INDICES_TO_INITIATE_FLOODFILL = buildFloodfillIndices();

    // Opaque cells.
    private final BitSet blocks;
    // Bounds of the renderable cells.
    private final RenderableBounds renderableBounds = new RenderableBounds();

    // Returns the indices of the cells on the surface.
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

    // Creates a builder without opaque cells.
    public SectionVisibilityBuilder() {
        this.blocks = new BitSet(SECTION_AXIS_SIZE * SECTION_AXIS_SIZE * SECTION_AXIS_SIZE);
    }

    // Returns the encoding of which faces see each other.
    public long computeVisibilityEncoding() {
        int opaqueCount = blocks.cardinality();
        if (opaqueCount == TOTAL_BLOCKS) {
            // Returns no visibility for a full section
            return VisibilityEncoding.NULL;
        } else if (opaqueCount < BLOCKS_ON_ONE_FACE) {
            // Returns full visibility when no face can be covered
            return VisibilityEncoding.EVERYTHING;
        } else {
            return computeWithFloodFill();
        }
    }

    // Marks a cell as opaque.
    public void markOpaque(int x, int y, int z) {
        this.blocks.set(getIndex(x & 15, y & 15, z & 15));
    }

    // Marks a cell as renderable.
    public void markRenderable(int x, int y, int z) {
        this.renderableBounds.mark(x & 15, y & 15, z & 15);
    }

    // Marks the whole section as renderable.
    public void markSectionRenderable() {
        this.renderableBounds.mark(0, 0, 0);
        this.renderableBounds.mark(SECTION_AXIS_MASK, SECTION_AXIS_MASK, SECTION_AXIS_MASK);
    }

    // Returns the bounds followed by the occluder boxes or null for an empty section.
    public int @Nullable [] computeOccluderBoxes() {
        return dataFrom(this.blocks, this.renderableBounds);
    }

    // Returns the bounds of the occluder data.
    static int bounds(int[] data) {
        return data[0];
    }

    // Combines the bounds and the boxes into one array.
    private static int @Nullable [] dataFrom(BitSet blocks, RenderableBounds bounds) {
        if (blocks.isEmpty() && bounds.isEmpty()) {
            return null;
        }

        int[] boxes = blocks.isEmpty() ? null
                : OccluderBoxes.from(Arrays.copyOf(blocks.toLongArray(), BoxFinder.INTERIOR_CACHE_WORDS));

        int count = boxes == null ? 0 : boxes.length;
        int[] data = new int[count + 1];
        data[0] = bounds.packed();

        if (boxes != null) {
            System.arraycopy(boxes, 0, data, 1, count);
        }

        return data;
    }

    // Returns the encoding found by filling the open cells from the surface.
    private long computeWithFloodFill() {
        long resultEncoding = 0;
        // Fills a copy of the opaque cells.
        BitSet visited = (BitSet) this.blocks.clone();
        BitSet escapedFaces = new BitSet(GraphDirection.COUNT);
        IntArrayFIFOQueue queue = new IntArrayFIFOQueue();
        for (int i : INDICES_TO_INITIATE_FLOODFILL) {
            if (!visited.get(i)) {
                escapedFaces.clear();
                queue.clear();
                exploreFrom(visited, escapedFaces, queue, i);
                for (int dir = escapedFaces.nextSetBit(0); dir >= 0; dir = escapedFaces.nextSetBit(dir+1)) {
                    for (int dir2 = escapedFaces.nextSetBit(0); dir2 >= 0; dir2 = escapedFaces.nextSetBit(dir2+1)) {
                        resultEncoding |= 1L << VisibilityEncoding.bit(dir, dir2);
                    }
                }
            }
        }
        return resultEncoding;
    }

    // Fills the open cells reachable from a cell and records the faces reached.
    private static void exploreFrom(BitSet visited, BitSet escapedFaces, IntArrayFIFOQueue queue, int startIndex) {
        queue.enqueue(startIndex);
        // Marks the start as visited
        visited.set(startIndex, true);
        while (!queue.isEmpty()) {
            int idx = queue.dequeueInt();
            for (int dir = 0; dir < GraphDirection.COUNT; dir++) {
                int neighborIdx = getNeighborIndex(idx, dir);
                if (neighborIdx >= 0) {
                    // Moves inside the section
                    if (!visited.get(neighborIdx)) {
                        // Marks the cell as visited
                        visited.set(neighborIdx, true);
                        queue.enqueue(neighborIdx);
                    }
                } else {
                    // Records the face that was reached
                    escapedFaces.set(dir);
                }
            }
        }
    }

    // Returns the index of the neighbor in a direction or minus one outside the section.
    private static int getNeighborIndex(int idx, int dir) {
        switch (dir) {
            case GraphDirection.UP -> {
                if (((idx >> Y_SHIFT) & SECTION_AXIS_MASK) == SECTION_AXIS_MASK) {
                    return -1;
                } else {
                    return idx + (1 << Y_SHIFT);
                }
            }
            case GraphDirection.DOWN -> {
                if (((idx >> Y_SHIFT) & SECTION_AXIS_MASK) == 0) {
                    return -1;
                } else {
                    return idx - (1 << Y_SHIFT);
                }
            }
            case GraphDirection.EAST -> {
                if (((idx >> X_SHIFT) & SECTION_AXIS_MASK) == SECTION_AXIS_MASK) {
                    return -1;
                } else {
                    return idx + (1 << X_SHIFT);
                }
            }
            case GraphDirection.WEST -> {
                if (((idx >> X_SHIFT) & SECTION_AXIS_MASK) == 0) {
                    return -1;
                } else {
                    return idx - (1 << X_SHIFT);
                }
            }
            case GraphDirection.SOUTH -> {
                if (((idx >> Z_SHIFT) & SECTION_AXIS_MASK) == SECTION_AXIS_MASK) {
                    return -1;
                } else {
                    return idx + (1 << Z_SHIFT);
                }
            }
            case GraphDirection.NORTH -> {
                if (((idx >> Z_SHIFT) & SECTION_AXIS_MASK) == 0) {
                    return -1;
                } else {
                    return idx - (1 << Z_SHIFT);
                }
            }
            default -> throw new IllegalArgumentException();
        }
    }

    // Returns the index of a cell.
    static int getIndex(int x, int y, int z) {
        return (y << Y_SHIFT) | (z << Z_SHIFT) | (x << X_SHIFT);
    }
}
