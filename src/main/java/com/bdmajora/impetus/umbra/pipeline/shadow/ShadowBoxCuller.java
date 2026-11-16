package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;

// Frustum that keeps boxes inside a distance of the camera.
public final class ShadowBoxCuller implements Frustum {
    // Largest distance from the camera.
    private final float maxDistance;

    // Creates a shadow box culler from its max distance.
    public ShadowBoxCuller(float maxDistance) {
        this.maxDistance = maxDistance;
    }

    // Returns whether a box is within the distance.
    @Override
    public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float distance = this.maxDistance;
        return !(maxX < -distance || minX > distance
                || maxY < -distance || minY > distance
                || maxZ < -distance || minZ > distance);
    }
}
