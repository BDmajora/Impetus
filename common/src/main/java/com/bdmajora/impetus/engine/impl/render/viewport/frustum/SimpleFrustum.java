package com.bdmajora.impetus.engine.impl.render.viewport.frustum;

import org.joml.FrustumIntersection;

// Frustum that delegates to a JOML frustum.
public final class SimpleFrustum implements Frustum {
    // Wrapped frustum.
    private final FrustumIntersection frustum;

    // Creates a frustum and rejects null.
    public SimpleFrustum(FrustumIntersection frustumIntersection) {
        this.frustum = java.util.Objects.requireNonNull(frustumIntersection, "frustumIntersection");
    }

    // Returns whether a box intersects the frustum.
    @Override
    public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return this.frustum.testAab(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // Returns how a box relates to the frustum.
    @Override
    public int intersectAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return switch (this.frustum.intersectAab(minX, minY, minZ, maxX, maxY, maxZ)) {
            case FrustumIntersection.INSIDE -> Frustum.FULLY_INSIDE;
            case FrustumIntersection.INTERSECT -> Frustum.PARTIALLY_INSIDE;
            default -> Frustum.OUTSIDE;
        };
    }
}
