package com.bdmajora.impetus.umbra.pipeline.shadow;

import org.joml.Matrix4fc;
import org.joml.Vector3f;

// Shadow frustum that never culls near the camera.
public final class SafeZoneCullingFrustum extends AdvancedShadowCullingFrustum {
    // Distance culler.
    private final ShadowBoxCuller distanceCuller;

    // Creates a safe zone culling frustum from its model view projection and shadow light vector from origin.
    public SafeZoneCullingFrustum(Matrix4fc modelViewProjection, Vector3f shadowLightVectorFromOrigin,
                                  // Voxel and distance cullers.
                                  ShadowBoxCuller voxelCuller, ShadowBoxCuller distanceCuller) {
        super(modelViewProjection, shadowLightVectorFromOrigin, voxelCuller);
        this.distanceCuller = distanceCuller;
    }

    // Returns whether a box survives culling.
    @Override
    public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        if (this.distanceCuller != null && !this.distanceCuller.testAab(minX, minY, minZ, maxX, maxY, maxZ)) {
            return false;
        }
        if (this.boxCuller != null && this.boxCuller.testAab(minX, minY, minZ, maxX, maxY, maxZ)) {
            return true;
        }
        return checkCornerVisibility(minX, minY, minZ, maxX, maxY, maxZ) != OUTSIDE;
    }
}
