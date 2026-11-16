package com.bdmajora.impetus.engine.impl.render.viewport.frustum;

// Frustum of a shadow pass that can drive a search from the visible sections.
public interface ShadowSearchFrustum {
    // Returns whether the shadow map is only used for points the camera sees.
    boolean supportsOcclusionSearch();

    // Returns the x of the direction to the light.
    float shadowLightX();

    // Returns the y of the direction to the light.
    float shadowLightY();

    // Returns the z of the direction to the light.
    float shadowLightZ();
}
