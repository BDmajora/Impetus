package com.bdmajora.impetus.engine.impl.model.light;

import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;

// Source of diffuse shading for a normal.
public interface DiffuseProvider {
    // Provider that never darkens.
    DiffuseProvider NONE = (normalX, normalY, normalZ, shade) -> 1.0f;

    // Returns the diffuse factor of a normal.
    float getDiffuse(float normalX, float normalY, float normalZ, boolean shade);

    // Returns the diffuse factor of a face.
    default float getDiffuse(ModelQuadFacing lightFace, boolean shade) {
        return getDiffuse(lightFace.getStepX(), lightFace.getStepY(), lightFace.getStepZ(), shade);
    }
}
