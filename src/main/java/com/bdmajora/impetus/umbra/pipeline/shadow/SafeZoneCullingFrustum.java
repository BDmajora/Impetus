package com.bdmajora.impetus.umbra.pipeline.shadow;

import org.joml.Matrix4fc;
import org.joml.Vector3f;

// Advanced shadow frustum that always accepts boxes in an inner zone and always rejects boxes past an outer bound
public final class SafeZoneCullingFrustum extends AdvancedShadowCullingFrustum {
    // Outer distance box past which every box is rejected or null for no outer bound
    private final ShadowBoxCuller distanceCuller;

    // Builds the frustum with an inner zone box and an outer distance box where either may be null
    public SafeZoneCullingFrustum(Matrix4fc modelViewProjection, Vector3f shadowLightVectorFromOrigin,
                                  ShadowBoxCuller voxelCuller, ShadowBoxCuller distanceCuller) {
        super(modelViewProjection, shadowLightVectorFromOrigin, voxelCuller);
        this.distanceCuller = distanceCuller;
    }

    // Rejects boxes past the outer bound and accepts boxes in the inner zone and tests the planes otherwise
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
