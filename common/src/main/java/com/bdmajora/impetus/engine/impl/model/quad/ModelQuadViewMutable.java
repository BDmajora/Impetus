package com.bdmajora.impetus.engine.impl.model.quad;

import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;

// Mutable view of a model quad.
public interface ModelQuadViewMutable extends ModelQuadView {
    void setX(int idx, float x);

    void setY(int idx, float y);

    void setZ(int idx, float z);

    void setColor(int idx, int color);

    void setTexU(int idx, float u);

    void setTexV(int idx, float v);

    void setLight(int idx, int light);

    void setFlags(int flags);

    void setSprite(Object sprite);

    void setColorIndex(int index);

    void setLightFace(ModelQuadFacing face);

    void setHasAmbientOcclusion(boolean hasAmbientOcclusion);

    // Normal is packed/embedded directly in the vertex data
    void setForgeNormal(int idx, int normal);
}
