package com.bdmajora.impetus.engine.impl.model.quad;

import static com.bdmajora.impetus.engine.impl.util.ModelQuadUtil.*;

import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.util.QuadUtil;

// On-heap scratch implementation of ModelQuadViewMutable for holding quad vertex data
public class ModelQuad implements ModelQuadViewMutable {
    private final int[] data = new int[VERTEX_SIZE * 4];
    private int flags;

    private int normal;

    private Object sprite;
    private int colorIdx;
    private ModelQuadFacing direction;

    private boolean hasAmbientOcclusion = true;

    // Vertex position
    @Override
    public void setX(int idx, float x) {
        this.data[vertexOffset(idx) + POSITION_INDEX] = Float.floatToRawIntBits(x);
        this.normal = 0;
    }

    // Vertex position
    @Override
    public void setY(int idx, float y) {
        this.data[vertexOffset(idx) + POSITION_INDEX + 1] = Float.floatToRawIntBits(y);
        this.normal = 0;
    }

    // Vertex position
    @Override
    public void setZ(int idx, float z) {
        this.data[vertexOffset(idx) + POSITION_INDEX + 2] = Float.floatToRawIntBits(z);
        this.normal = 0;
    }

    // Vertex colour, ABGR
    @Override
    public void setColor(int idx, int color) {
        this.data[vertexOffset(idx) + COLOR_INDEX] = color;
    }

    // Vertex UV
    @Override
    public void setTexU(int idx, float u) {
        this.data[vertexOffset(idx) + TEXTURE_INDEX] = Float.floatToRawIntBits(u);
    }

    // Vertex UV
    @Override
    public void setTexV(int idx, float v) {
        this.data[vertexOffset(idx) + TEXTURE_INDEX + 1] = Float.floatToRawIntBits(v);
    }

    // Packed lightmap coordinates
    @Override
    public void setLight(int idx, int light) {
        this.data[vertexOffset(idx) + LIGHT_INDEX] = light;
    }

    // Replaces the flag bits
    @Override
    public void setFlags(int flags) {
        this.flags = flags;
    }

    // The atlas sprite, untyped to stay platform-free
    @Override
    public void setSprite(Object sprite) {
        this.sprite = sprite;
    }

    // Tint index, -1 for none
    @Override
    public void setColorIndex(int index) {
        this.colorIdx = index;
    }

    // The face used for shading and culling
    @Override
    public void setLightFace(ModelQuadFacing face) {
        if (!face.isDirection()) {
            throw new IllegalArgumentException();
        }
        this.direction = face;
    }

    // Whether AO applies to this quad
    @Override
    public void setHasAmbientOcclusion(boolean hasAmbientOcclusion) {
        this.hasAmbientOcclusion = hasAmbientOcclusion;
    }

    // Tint index
    @Override
    public int getColorIndex() {
        return this.colorIdx;
    }

    // Vertex position
    @Override
    public float getX(int idx) {
        return Float.intBitsToFloat(this.data[vertexOffset(idx) + POSITION_INDEX]);
    }

    // Vertex position
    @Override
    public float getY(int idx) {
        return Float.intBitsToFloat(this.data[vertexOffset(idx) + POSITION_INDEX + 1]);
    }

    // Vertex position
    @Override
    public float getZ(int idx) {
        return Float.intBitsToFloat(this.data[vertexOffset(idx) + POSITION_INDEX + 2]);
    }

    // Vertex colour, ABGR
    @Override
    public int getColor(int idx) {
        return this.data[vertexOffset(idx) + COLOR_INDEX];
    }

    // Vertex UV
    @Override
    public float getTexU(int idx) {
        return Float.intBitsToFloat(this.data[vertexOffset(idx) + TEXTURE_INDEX]);
    }

    // Vertex UV
    @Override
    public float getTexV(int idx) {
        return Float.intBitsToFloat(this.data[vertexOffset(idx) + TEXTURE_INDEX + 1]);
    }

    // Packed lightmap coordinates
    @Override
    public int getLight(int idx) {
        return this.data[vertexOffset(idx) + LIGHT_INDEX];
    }

    // Per-vertex packed normal, zero when absent
    @Override
    public int getForgeNormal(int idx) {
        return this.data[vertexOffset(idx) + NORMAL_INDEX];
    }

    // Per-vertex packed normal
    @Override
    public void setForgeNormal(int idx, int normal) {
        this.data[vertexOffset(idx) + NORMAL_INDEX] = normal;
    }

    // Lazily computed from positions and cached
    @Override
    public int getComputedFaceNormal() {
        int n = this.normal;
        if (n == 0) {
            this.normal = n = QuadUtil.calculateNormal(this);
        }
        return n;
    }

    // Derived from the computed normal
    @Override
    public ModelQuadFacing getNormalFace() {
        return QuadUtil.findNormalFace(getComputedFaceNormal());
    }

    // Lazily populated on first read
    @Override
    public int getFlags() {
        return this.flags;
    }

    @Override
    public Object impetus$getSprite() {
        return this.sprite;
    }

    // The face used for shading and culling
    @Override
    public ModelQuadFacing getLightFace() {
        return this.direction;
    }

    // Whether AO applies
    @Override
    public boolean hasAmbientOcclusion() {
        return this.hasAmbientOcclusion;
    }
}
