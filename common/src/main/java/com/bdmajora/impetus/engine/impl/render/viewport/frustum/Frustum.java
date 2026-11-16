package com.bdmajora.impetus.engine.impl.render.viewport.frustum;

// Volume of the view that boxes are tested against.
public interface Frustum {
    // Result of a box that is fully inside.
    int FULLY_INSIDE = 0;
    // Result of a box that is partly inside.
    int PARTIALLY_INSIDE = 1;
    // Result of a box that is outside.
    int OUTSIDE = 2;

    // Returns whether a box intersects the frustum.
    boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ);

    // Returns how a box relates to the frustum.
    default int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return testAab(minX, minY, minZ, maxX, maxY, maxZ) ? PARTIALLY_INSIDE : OUTSIDE;
    }
}
