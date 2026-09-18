package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;
import com.bdmajora.impetus.umbra.pipeline.ShadowContentSettings;
import com.bdmajora.impetus.umbra.uniforms.CapturedRenderingState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

// Picks the shadow section filter per Iris's decision tree: distance-only when the pack voxelises and stated no preference, SafeZoneCullingFrustum for reversed, the advanced frustum otherwise
public final class ShadowFrustums {

    // Accepts every section; Iris returns NonCullingFrustum only when the pack turned culling off or a distance-only pass already covers the render distance, every other branch keeps a real frustum
    public static final Frustum NON_CULLING = (minX, minY, minZ, maxX, maxY, maxZ) -> true;

    private ShadowFrustums() {
    }

    // shadowDistance and voxelDistance are the pack's directives in blocks (0 when undeclared), packVoxelizes is inferred from a geometry stage or custom images, renderDistance is the player's in blocks, sunPathRotation feeds the light vector
    public static Frustum create(ShadowContentSettings.Culling culling, float shadowDistance, float voxelDistance,
                                 boolean packVoxelizes, int renderDistance, float sunPathRotation) {
        return create(culling, shadowDistance, voxelDistance, packVoxelizes, renderDistance, sunPathRotation,
                CapturedRenderingState.INSTANCE.getGbufferProjection());
    }

    // Same with an explicit view projection: the camera's normally, DH's (its far plane) when LODs cast into the map
    public static Frustum create(ShadowContentSettings.Culling culling, float shadowDistance, float voxelDistance,
                                 boolean packVoxelizes, int renderDistance, float sunPathRotation,
                                 Matrix4fc viewProjection) {
        // Culling explicitly off: draw it all.
        if (culling == ShadowContentSettings.Culling.OFF) {
            return NON_CULLING;
        }

        // Umbra parity: a voxelizing pack with no stated mode gets distance-only culling since the advanced frustum's view dependence destabilizes its voxel field; the ONLY branch where Umbra degrades to no culling when the distance covers the render distance
        if (culling == ShadowContentSettings.Culling.ON && packVoxelizes) {
            if (shadowDistance <= 0.0f || shadowDistance > renderDistance) {
                return NON_CULLING;
            }
            return new ShadowBoxCuller(shadowDistance);
        }

        Vector3f lightVector = shadowLightVectorFromOrigin(sunPathRotation);
        Matrix4f projView = new Matrix4f(viewProjection)
                .mul(CapturedRenderingState.INSTANCE.getGbufferModelView());

        if (culling == ShadowContentSettings.Culling.REVERSED) {
            // `reversed`/`safe_zone`: everything within voxelDistance is drawn unconditionally with shadowDistance as the outer bound; Umbra exempts this mode from the render-distance bailout, and applying it here collapsed Complementary (shadowDistance 256) to NON_CULLING under 16 chunks
            return new SafeZoneCullingFrustum(projView, lightVector,
                    new ShadowBoxCuller(voxelDistance), new ShadowBoxCuller(shadowDistance));
        }

        // Umbra drops only the *box* culler when the shadow distance covers the render distance; the direction-dependent planes still keep off-screen casters casting, and a null culler means no distance bound
        if (shadowDistance <= 0.0f || shadowDistance >= renderDistance) {
            return new AdvancedShadowCullingFrustum(projView, lightVector, null);
        }

        return new AdvancedShadowCullingFrustum(projView, lightVector, new ShadowBoxCuller(shadowDistance));
    }

    // The normalised vector from the origin toward the shadow light, which decides the frustum's "back" planes that keep casters behind the camera; derived like CelestialUniforms.getShadowLightPositionInWorldSpace
    @SuppressWarnings("unused") // sunPathRotation is already baked into CelestialUniforms' static state
    private static Vector3f shadowLightVectorFromOrigin(float sunPathRotation) {
        Vector3f vector = com.bdmajora.impetus.umbra.uniforms.CelestialUniforms
                .getShadowLightPositionInWorldSpace();
        if (vector.lengthSquared() == 0.0f) {
            // Degenerate (no celestial state yet): pick straight up so the frustum stays well-formed.
            return new Vector3f(0.0f, 1.0f, 0.0f);
        }
        return vector.normalize();
    }
}
