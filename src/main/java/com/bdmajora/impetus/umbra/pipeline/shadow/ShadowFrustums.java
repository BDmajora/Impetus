package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;
import com.bdmajora.impetus.umbra.pipeline.ShadowContentSettings;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.bdmajora.impetus.umbra.uniforms.CelestialUniforms;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

// Factory of shadow culling frustums.
public final class ShadowFrustums {
    // Frustum that keeps everything.
    public static final Frustum NON_CULLING = (minX, minY, minZ, maxX, maxY, maxZ) -> true;

    // Prevents instantiation
    private ShadowFrustums() {
    }

    // Creates a frustum without a box limit.
    public static Frustum create(ShadowContentSettings.Culling culling, float shadowDistance, float voxelDistance,
                                 // Voxel switch and render distance and sun path rotation.
                                 boolean packVoxelizes, int renderDistance, float sunPathRotation) {
        return create(culling, shadowDistance, voxelDistance, packVoxelizes, renderDistance, sunPathRotation,
                CapturedRenderingState.INSTANCE.getGbufferProjection());
    }

    // Creates a frustum for a culling mode.
    public static Frustum create(ShadowContentSettings.Culling culling, float shadowDistance, float voxelDistance,
                                 // Voxel switch and render distance and sun path rotation.
                                 boolean packVoxelizes, int renderDistance, float sunPathRotation,
                                 Matrix4fc viewProjection) {
        if (culling == ShadowContentSettings.Culling.OFF) {
            return NON_CULLING;
        }
        if (culling == ShadowContentSettings.Culling.ON && packVoxelizes) {
            if (shadowDistance <= 0.0f || shadowDistance > renderDistance) {
                return NON_CULLING;
            }
            return new ShadowBoxCuller(shadowDistance);
        }
        Vector3f lightVector = shadowLightVectorFromOrigin();
        Matrix4f projView = new Matrix4f(viewProjection).mul(CapturedRenderingState.INSTANCE.getGbufferModelView());
        if (culling == ShadowContentSettings.Culling.REVERSED) {
            return new SafeZoneCullingFrustum(projView, lightVector,
                    new ShadowBoxCuller(voxelDistance), new ShadowBoxCuller(shadowDistance));
        }
        if (shadowDistance <= 0.0f || shadowDistance >= renderDistance) {
            return new AdvancedShadowCullingFrustum(projView, lightVector, null);
        }
        return new AdvancedShadowCullingFrustum(projView, lightVector, new ShadowBoxCuller(shadowDistance));
    }

    // Returns the light direction of the shadow pass.
    static Vector3f shadowLightVectorFromOrigin() {
        Vector3f vector = CelestialUniforms.getShadowLightPositionInWorldSpace();
        float lengthSquared = vector.lengthSquared();
        if (!(lengthSquared > 0.0f && lengthSquared < Float.POSITIVE_INFINITY)) {
            return new Vector3f(0.0f, 1.0f, 0.0f);
        }
        return vector.normalize();
    }
}
