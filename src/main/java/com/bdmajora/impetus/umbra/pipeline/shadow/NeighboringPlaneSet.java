package com.bdmajora.impetus.umbra.pipeline.shadow;

import java.util.Objects;

// Data of the neighboring plane set.
public record NeighboringPlaneSet(int plane0, int plane1, int plane2, int plane3) {
    // Number of planes.
    private static final int PLANE_COUNT = 6;
    // Neighbouring planes of the positive x plane.
    private static final NeighboringPlaneSet FOR_PLUS_X = new NeighboringPlaneSet(2, 3, 4, 5);
    // Neighbouring planes of the positive y plane.
    private static final NeighboringPlaneSet FOR_PLUS_Y = new NeighboringPlaneSet(0, 1, 4, 5);
    // Neighbouring planes of the positive z plane.
    private static final NeighboringPlaneSet FOR_PLUS_Z = new NeighboringPlaneSet(0, 1, 2, 3);
    // Plane sets by axis.
    private static final NeighboringPlaneSet[] TABLE = {FOR_PLUS_X, FOR_PLUS_Y, FOR_PLUS_Z};

    // Returns the neighbours of a plane index from 0 to 5.
    public static NeighboringPlaneSet forPlane(int planeIndex) {
        return TABLE[Objects.checkIndex(planeIndex, PLANE_COUNT) >>> 1];
    }
}
