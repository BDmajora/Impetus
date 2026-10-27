package com.bdmajora.impetus.umbra.pipeline.shadow;

import java.util.Objects;

// Indices of the four frustum planes that share an edge with a given plane
public record NeighboringPlaneSet(int plane0, int plane1, int plane2, int plane3) {
    // Number of planes in a view frustum
    private static final int PLANE_COUNT = 6;
    // Neighbours of the two X planes
    private static final NeighboringPlaneSet FOR_PLUS_X = new NeighboringPlaneSet(2, 3, 4, 5);
    // Neighbours of the two Y planes
    private static final NeighboringPlaneSet FOR_PLUS_Y = new NeighboringPlaneSet(0, 1, 4, 5);
    // Neighbours of the two Z planes
    private static final NeighboringPlaneSet FOR_PLUS_Z = new NeighboringPlaneSet(0, 1, 2, 3);
    // Neighbour sets indexed by axis
    private static final NeighboringPlaneSet[] TABLE = {FOR_PLUS_X, FOR_PLUS_Y, FOR_PLUS_Z};

    // Returns the neighbours of a plane index between 0 and 5 and rejects any other index
    public static NeighboringPlaneSet forPlane(int planeIndex) {
        return TABLE[Objects.checkIndex(planeIndex, PLANE_COUNT) >>> 1];
    }
}
