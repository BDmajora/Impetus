package com.bdmajora.impetus.engine.impl.model.light.smooth;

import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import com.bdmajora.impetus.engine.impl.model.light.DiffuseProvider;
import com.bdmajora.impetus.engine.impl.model.light.LightPipeline;
import com.bdmajora.impetus.engine.impl.model.light.data.LightDataAccess;
import com.bdmajora.impetus.engine.impl.model.light.data.QuadLightData;
import com.bdmajora.impetus.engine.impl.model.quad.ModelQuadView;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFlags;
import com.bdmajora.impetus.engine.api.util.NormI8;
import com.bdmajora.impetus.engine.impl.util.PositionUtil;

import java.util.Objects;

// Smooth lighter that blends the corner light of each face.
public class SmoothLightPipeline implements LightPipeline {
    // Cache the light data is read from.
    private final LightDataAccess lightCache;

    // Face data of each side of a block with and without an offset.
    private final AoFaceData[] cachedFaceData = new AoFaceData[6 * 2];

    // Block position of the cached face data.
    private long cachedPos = Long.MIN_VALUE;

    // Scratch array of corner weights.
    private final float[] weights = new float[4];

    // Quad normal shading state of the lighter.
    private final boolean useQuadNormalsForShading;

    // Source of the diffuse shading.
    private final DiffuseProvider diffuseProvider;

    // Occlusion and block and sky light of the last vertex.
    private float lastAo, lastBl, lastSl;

    // Creates a lighter and rejects a null cache or diffuse provider.
    public SmoothLightPipeline(LightDataAccess cache, DiffuseProvider diffuseProvider, boolean useQuadNormalsForShading) {
        Objects.requireNonNull(diffuseProvider, "diffuseProvider");
        this.lightCache = Objects.requireNonNull(cache, "cache");

        for (int i = 0; i < this.cachedFaceData.length; i++) {
            this.cachedFaceData[i] = new AoFaceData();
        }

        this.useQuadNormalsForShading = useQuadNormalsForShading;
        this.diffuseProvider = diffuseProvider;
    }

    // Calculates the brightness and lightmap of the four vertices of a quad.
    @Override
    public void calculate(ModelQuadView quad, int x, int y, int z, QuadLightData out, ModelQuadFacing cullFace, ModelQuadFacing lightFace,
                          boolean shade, boolean applyAoDepthBlending) {
        this.updateCachedData(PositionUtil.packBlock(x, y, z));

        int flags = quad.getFlags();

        final AoNeighborInfo neighborInfo = AoNeighborInfo.get(lightFace);

        // Takes the aligned path for aligned quads and parallel quads of a full cube
        if ((flags & ModelQuadFlags.IS_ALIGNED) != 0 || ((flags & ModelQuadFlags.IS_PARALLEL) != 0 && LightDataAccess.unpackFC(this.lightCache.get(x, y, z)))) {
            if ((flags & ModelQuadFlags.IS_PARTIAL) == 0) {
                this.applyAlignedFullFace(neighborInfo, x, y, z, lightFace, out);
            } else {
                this.applyAlignedPartialFace(neighborInfo, quad, x, y, z, lightFace, out);
            }
        } else {
            if ((flags & ModelQuadFlags.IS_VANILLA_SHADED) == 0 && quad.getNormalFace() == ModelQuadFacing.UNASSIGNED) {
                // Handles a normal with several components
                this.applyIrregularFace(quad, x, y, z, out, applyAoDepthBlending);
            } else {
                // Handles a normal with one component
                this.applyNonParallelFace(neighborInfo, quad, x, y, z, lightFace, out, applyAoDepthBlending);
            }
        }

        if ((flags & ModelQuadFlags.IS_VANILLA_SHADED) != 0 || !this.useQuadNormalsForShading) {
            this.applySidedBrightness(out, lightFace, shade);
        } else {
            this.applySidedBrightnessFromNormals(out, quad, shade);
        }
    }

    // Clears the cached face data and forgets the cached position.
    @Override
    public void reset() {
        for (AoFaceData data : this.cachedFaceData) {
            data.reset();
        }
        this.cachedPos = Long.MIN_VALUE;
    }

    // Maps the corner data of a face straight onto a full aligned quad.
    private void applyAlignedFullFace(AoNeighborInfo neighborInfo, int x, int y, int z, ModelQuadFacing dir, QuadLightData out) {
        AoFaceData faceData = this.getCachedFaceData(x, y, z, dir, true);
        neighborInfo.mapCorners(faceData.lm, faceData.ao, out.lm, out.br);
    }

    // Calculates the light of an aligned quad that covers part of its face.
    private void applyAlignedPartialFace(AoNeighborInfo neighborInfo, ModelQuadView quad, int x, int y, int z, ModelQuadFacing dir, QuadLightData out) {
        this.applyPerVertex(neighborInfo, quad, x, y, z, dir, out, false, true);
    }

    // Calculates the light of a quad that is neither aligned nor parallel.
    private void applyNonParallelFace(AoNeighborInfo neighborInfo, ModelQuadView quad, int x, int y, int z, ModelQuadFacing dir,
                                      QuadLightData out, boolean applyAoDepthBlending) {
        this.applyPerVertex(neighborInfo, quad, x, y, z, dir, out, true, applyAoDepthBlending);
    }

    // Calculates the light of each vertex from its corner weights.
    private void applyPerVertex(AoNeighborInfo neighborInfo, ModelQuadView quad, int x, int y, int z, ModelQuadFacing dir,
                                QuadLightData out, boolean useDepth, boolean applyAoDepthBlending) {
        float[] weights = this.weights;

        for (int i = 0; i < 4; i++) {
            // Limits the vertex to the block
            float cx = MathUtil.saturate(quad.getX(i));
            float cy = MathUtil.saturate(quad.getY(i));
            float cz = MathUtil.saturate(quad.getZ(i));

            neighborInfo.calculateCornerWeights(cx, cy, cz, weights);

            if (!useDepth) {
                this.applyAlignedPartialFaceVertex(x, y, z, dir, weights, true);
            } else {
                float depth = neighborInfo.getDepth(cx, cy, cz);

                if (applyAoDepthBlending) {
                    // Blends the near and far data by depth
                    this.applyInsetPartialFaceVertex(x, y, z, dir, depth, 1.0f - depth, weights);
                } else {
                    this.applyAlignedPartialFaceVertex(x, y, z, dir, weights, MathUtil.roughlyEqual(depth, 0.0F));
                }
            }

            out.br[i] = lastAo;
            out.lm[i] = getLightMapCoord(lastSl, lastBl);
        }
    }

    // Blends the near and far face data of a vertex that is set back from the face.
    private void applyInsetPartialFaceVertex(int x, int y, int z, ModelQuadFacing dir, float n1d, float n2d, float[] w) {
        // Skips blending near either end
        if (MathUtil.roughlyEqual(n1d, 0.0f)) {
            this.applyAlignedPartialFaceVertex(x, y, z, dir, w, true);
            return;
        }

        if (MathUtil.roughlyEqual(n1d, 1.0f)) {
            this.applyAlignedPartialFaceVertex(x, y, z, dir, w, false);
            return;
        }

        AoFaceData n1 = this.getUnpackedFaceData(x, y, z, dir, false);
        AoFaceData n2 = this.getUnpackedFaceData(x, y, z, dir, true);

        // Blends by the depth weights
        this.lastAo = (n1.getBlendedShade(w) * n1d) + (n2.getBlendedShade(w) * n2d);
        this.lastSl = (n1.getBlendedSkyLight(w) * n1d) + (n2.getBlendedSkyLight(w) * n2d);
        this.lastBl = (n1.getBlendedBlockLight(w) * n1d) + (n2.getBlendedBlockLight(w) * n2d);
    }

    // Share of the weighted average in an irregular face.
    private static final float BLENDED_WEIGHT = 0.75f;
    // Share of the maximum in an irregular face.
    private static final float MAX_WEIGHT = 1f - BLENDED_WEIGHT;

    // Calculates the light of a quad with an irregular normal.
    private void applyIrregularFace(ModelQuadView quad, int x, int y, int z, QuadLightData out, boolean applyAoDepthBlending) {
        for (int i = 0; i < 4; i++) {
            // Limits the vertex to the block
            float cx = MathUtil.saturate(quad.getX(i));
            float cy = MathUtil.saturate(quad.getY(i));
            float cz = MathUtil.saturate(quad.getZ(i));

            int normal = quad.getForgeNormal(i);
            if (normal == 0) {
                normal = quad.getComputedFaceNormal();
            }

            float weightedAo = 0, weightedBl = 0, weightedSl = 0;
            float maxAo = 0, maxBl = 0, maxSl = 0;

            // Combines the contribution of each axis
            for (int axis = 0; axis < 3; axis++) {
                // Reads the normal component
                float projectedNormal = NormI8.unpackX(normal >> (axis * 8));

                // Skips a zero component
                if (projectedNormal == 0) {
                    continue;
                }

                var dir = ModelQuadFacing.AXES[axis].getFacing(projectedNormal > 0);

                var neighborInfo = AoNeighborInfo.get(dir);

                float[] weights = this.weights;
                neighborInfo.calculateCornerWeights(cx, cy, cz, weights);

                float depth = neighborInfo.getDepth(cx, cy, cz);

                if (applyAoDepthBlending) {
                    // Blends the near and far data by depth
                    this.applyInsetPartialFaceVertex(x, y, z, dir, depth, 1.0f - depth, weights);
                } else {
                    // Uses the inset data once the face is inset
                    this.applyAlignedPartialFaceVertex(x, y, z, dir, weights, MathUtil.roughlyEqual(depth, 0.0F));
                }

                float ao = this.lastAo, sl = this.lastSl, bl = this.lastBl;

                float combineWeight = projectedNormal * projectedNormal;

                weightedAo += ao * combineWeight;
                weightedBl += bl * combineWeight;
                weightedSl += sl * combineWeight;

                maxAo = Math.max(ao, maxAo);
                maxSl = Math.max(sl, maxSl);
                maxBl = Math.max(bl, maxBl);
            }

            out.br[i] = MathUtil.saturate(weightedAo * BLENDED_WEIGHT + maxAo * MAX_WEIGHT);
            out.lm[i] = getLightMapCoord(weightedSl * BLENDED_WEIGHT + maxSl * MAX_WEIGHT, weightedBl * BLENDED_WEIGHT + maxBl * MAX_WEIGHT);
        }
    }

    // Calculates the light of a vertex from the face data with an optional offset.
    private void applyAlignedPartialFaceVertex(int x, int y, int z, ModelQuadFacing dir, float[] w, boolean offset) {
        AoFaceData faceData = this.getUnpackedFaceData(x, y, z, dir, offset);

        this.lastSl = faceData.getBlendedSkyLight(w);
        this.lastBl = faceData.getBlendedBlockLight(w);
        this.lastAo = faceData.getBlendedShade(w);
    }

    // Scales the brightness by the diffuse of the face.
    private void applySidedBrightness(QuadLightData out, ModelQuadFacing face, boolean shade) {
        scaleBrightness(out.br, this.diffuseProvider.getDiffuse(face, shade));
    }

    // Scales the brightness by the diffuse of the quad normal.
    private void applySidedBrightnessFromNormals(QuadLightData out, ModelQuadView quad, boolean shade) {
        int normal = quad.getModFaceNormal();
        scaleBrightness(out.br, this.diffuseProvider.getDiffuse(NormI8.unpackX(normal), NormI8.unpackY(normal), NormI8.unpackZ(normal), shade));
    }

    // Multiplies every brightness by a factor.
    private static void scaleBrightness(float[] br, float brightness) {
        for (int i = 0; i < br.length; i++) {
            br[i] *= brightness;
        }
    }

    // Returns the face data and reads it when missing.
    private AoFaceData getCachedFaceData(int x, int y, int z, ModelQuadFacing face, boolean offset) {
        AoFaceData data = this.cachedFaceData[offset ? face.ordinal() : face.ordinal() + 6];

        if (!data.hasLightData()) {
            data.initLightData(this.lightCache, x, y, z, face, offset);
        }

        return data;
    }

    // Returns the face data and unpacks it when needed.
    private AoFaceData getUnpackedFaceData(int x, int y, int z, ModelQuadFacing face, boolean offset) {
        AoFaceData data = this.getCachedFaceData(x, y, z, face, offset);

        if (!data.hasUnpackedLightData()) {
            data.unpackLightData();
        }

        return data;
    }

    // Clears the cache when the block position changes.
    private void updateCachedData(long key) {
        if (this.cachedPos != key) {
            for (AoFaceData data : this.cachedFaceData) {
                data.reset();
            }

            this.cachedPos = key;
        }
    }

    // Returns the packed lightmap of sky and block light.
    private static int getLightMapCoord(float sl, float bl) {
        return (((int) sl & 0xFF) << 16) | ((int) bl & 0xFF);
    }

}