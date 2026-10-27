package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;

// Accepts camera relative boxes that overlap a cube of a given half size around the camera
public final class ShadowBoxCuller implements Frustum {
    // Half the edge length of the cube in blocks
    private final float maxDistance;

    // Creates the culler for a half size in blocks
    public ShadowBoxCuller(float maxDistance) {
        this.maxDistance = maxDistance;
    }

    // Returns whether the box overlaps the cube on every axis and treats a NaN bound as overlapping
    @Override
    public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        float distance = this.maxDistance;
        return !(maxX < -distance || minX > distance
                || maxY < -distance || minY > distance
                || maxZ < -distance || minZ > distance);
    }
}
