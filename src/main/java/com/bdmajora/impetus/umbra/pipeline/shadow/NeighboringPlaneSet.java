package com.bdmajora.impetus.umbra.pipeline.shadow;

// The four frustum planes adjacent to a given plane, for extruding edge planes towards the light (Iris's NeighboringPlaneSet); only three instances since the two planes on an axis share neighbours, and planeIndex >>> 1 picks it
public record NeighboringPlaneSet(int plane0, int plane1, int plane2, int plane3) {
    private static final NeighboringPlaneSet FOR_PLUS_X = new NeighboringPlaneSet(2, 3, 4, 5);
    private static final NeighboringPlaneSet FOR_PLUS_Y = new NeighboringPlaneSet(0, 1, 4, 5);
    private static final NeighboringPlaneSet FOR_PLUS_Z = new NeighboringPlaneSet(0, 1, 2, 3);

    private static final NeighboringPlaneSet[] TABLE = {FOR_PLUS_X, FOR_PLUS_Y, FOR_PLUS_Z};

    // >>> 1 turns a plane index into its axis index, which is exactly the TABLE index
    public static NeighboringPlaneSet forPlane(int planeIndex) {
        return TABLE[planeIndex >>> 1];
    }
}
