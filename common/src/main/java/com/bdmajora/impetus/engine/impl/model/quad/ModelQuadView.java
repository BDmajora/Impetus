package com.bdmajora.impetus.engine.impl.model.quad;

import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;

// Read only view of a model quad.
public interface ModelQuadView {
    float getX(int idx);

    float getY(int idx);

    float getZ(int idx);

    int getColor(int idx);

    float getTexU(int idx);

    float getTexV(int idx);

    // Returns the packed lightmap coords or zero for regular lighting.
    int getLight(int idx);

    int getFlags();

    int getColorIndex();

    Object impetus$getSprite();

    ModelQuadFacing getLightFace();

    ModelQuadFacing getNormalFace();

    int getForgeNormal(int idx);

    int getComputedFaceNormal();

    // Returns the explicit normal of vertex 0 or the computed face normal.
    default int getModFaceNormal() {
        int normal = getForgeNormal(0);
        return normal != 0 ? normal : getComputedFaceNormal();
    }

    // -1 is the "no color" sentinel
    default boolean hasColor() {
        return this.getColorIndex() != -1;
    }

    // Defaults on
    default boolean hasAmbientOcclusion() { return true; }

    // Defaults to none
    default int getVanillaLightEmission() {
        return 0;
    }
}
