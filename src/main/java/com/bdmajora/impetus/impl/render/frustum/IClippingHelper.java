package com.bdmajora.impetus.impl.render.frustum;

import org.joml.FrustumIntersection;

// Contract of the i clipping helper.
public interface IClippingHelper {
    // Returns the joml frustum.
    FrustumIntersection impetus$getJomlFrustum();
}
