package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;
import com.bdmajora.impetus.umbra.pipeline.ShadowContentSettings;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import com.bdmajora.impetus.umbra.uniforms.CelestialUniforms;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

// Chooses the section filter of the shadow pass from the culling mode and the pack distances
public final class ShadowFrustums {
    // Filter that accepts every box
    public static final Frustum NON_CULLING = (minX, minY, minZ, maxX, maxY, maxZ) -> true;

    // Prevents instantiation
    private ShadowFrustums() {
    }

    // Creates the filter against the captured camera projection
    public static Frustum create(ShadowContentSettings.Culling culling, float shadowDistance, float voxelDistance,
                                 boolean packVoxelizes, int renderDistance, float sunPathRotation) {
        return create(culling, shadowDistance, voxelDistance, packVoxelizes, renderDistance, sunPathRotation,
                CapturedRenderingState.INSTANCE.getGbufferProjection());
    }

    // Creates the filter against an explicit view projection with distances in blocks where 0 means undeclared
    public static Frustum create(ShadowContentSettings.Culling culling, float shadowDistance, float voxelDistance,
                                 boolean packVoxelizes, int renderDistance, float sunPathRotation,
                                 Matrix4fc viewProjection) {
        if (culling == ShadowContentSettings.Culling.OFF) {
            return NON_CULLING;
        }
        // A voxelizing pack with the default mode gets a distance box alone or no filter when the box covers the render distance
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
        // The planes stay and only the distance box drops when the shadow distance is unset or covers the render distance
        if (shadowDistance <= 0.0f || shadowDistance >= renderDistance) {
            return new AdvancedShadowCullingFrustum(projView, lightVector, null);
        }
        return new AdvancedShadowCullingFrustum(projView, lightVector, new ShadowBoxCuller(shadowDistance));
    }

    // Returns the unit vector toward the shadow light or straight up when the light position has no finite length
    static Vector3f shadowLightVectorFromOrigin() {
        Vector3f vector = CelestialUniforms.getShadowLightPositionInWorldSpace();
        float lengthSquared = vector.lengthSquared();
        if (!(lengthSquared > 0.0f && lengthSquared < Float.POSITIVE_INFINITY)) {
            return new Vector3f(0.0f, 1.0f, 0.0f);
        }
        return vector.normalize();
    }
}
