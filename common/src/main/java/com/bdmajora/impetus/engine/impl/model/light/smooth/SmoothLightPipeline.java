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

// Vanilla's smooth lighting ported from Sodium: gathers the neighbourhood once per face and bilinearly blends the four corners per vertex, with depth blending for inset quads
public class SmoothLightPipeline implements LightPipeline {
    // the cache the light data is read from
    private final LightDataAccess lightCache;

    // the cached face data for each side of a block, both inset and outset
    private final AoFaceData[] cachedFaceData = new AoFaceData[6 * 2];

    // the position the cached face data was taken at
    private long cachedPos = Long.MIN_VALUE;

    // scratch array holding the intermediary weight data for non-aligned face blending
    private final float[] weights = new float[4];

    // whether to even attempt to shade quads using their normals rather than their light face
    private final boolean useQuadNormalsForShading;

    // supplies the directional shading value for quads
    private final DiffuseProvider diffuseProvider;

    private float lastAo, lastBl, lastSl;

    public SmoothLightPipeline(LightDataAccess cache, DiffuseProvider diffuseProvider, boolean useQuadNormalsForShading) {
        this.lightCache = cache;

        for (int i = 0; i < this.cachedFaceData.length; i++) {
            this.cachedFaceData[i] = new AoFaceData();
        }

        this.useQuadNormalsForShading = useQuadNormalsForShading;
        this.diffuseProvider = diffuseProvider;
    }

    @Override
    public void calculate(ModelQuadView quad, int x, int y, int z, QuadLightData out, ModelQuadFacing cullFace, ModelQuadFacing lightFace,
                          boolean shade, boolean applyAoDepthBlending) {
        this.updateCachedData(PositionUtil.packBlock(x, y, z));

        int flags = quad.getFlags();

        final AoNeighborInfo neighborInfo = AoNeighborInfo.get(lightFace);

        // Aligned quads covering the whole face take the fast path of mapping corner values straight onto the vertices; a parallel face on a full cube counts as aligned to match vanilla
        if ((flags & ModelQuadFlags.IS_ALIGNED) != 0 || ((flags & ModelQuadFlags.IS_PARALLEL) != 0 && LightDataAccess.unpackFC(this.lightCache.get(x, y, z)))) {
            if ((flags & ModelQuadFlags.IS_PARTIAL) == 0) {
                this.applyAlignedFullFace(neighborInfo, x, y, z, lightFace, out);
            } else {
                this.applyAlignedPartialFace(neighborInfo, quad, x, y, z, lightFace, out);
            }
        } else {
            if ((flags & ModelQuadFlags.IS_VANILLA_SHADED) == 0 && quad.getNormalFace() == ModelQuadFacing.UNASSIGNED) {
                // Normal has multiple nonzero components
                this.applyIrregularFace(quad, x, y, z, out, applyAoDepthBlending);
            } else {
                // Normal has a single nonzero component
                this.applyNonParallelFace(neighborInfo, quad, x, y, z, lightFace, out, applyAoDepthBlending);
            }
        }

        if((flags & ModelQuadFlags.IS_VANILLA_SHADED) != 0 || !this.useQuadNormalsForShading) {
            this.applySidedBrightness(out, lightFace, shade);
        } else {
            this.applySidedBrightnessFromNormals(out, quad, shade);
        }
    }

    // Clears the per-face cache between blocks
    @Override
    public void reset() {
        this.cachedPos = Long.MIN_VALUE;
    }

    // Fast path for a full grid-aligned quad (IS_ALIGNED, !IS_PARTIAL): the most common case, each corner has only two contributing sides so no interpolation is needed
    private void applyAlignedFullFace(AoNeighborInfo neighborInfo, int x, int y, int z, ModelQuadFacing dir, QuadLightData out) {
        AoFaceData faceData = this.getCachedFaceData(x, y, z, dir, true);
        neighborInfo.mapCorners(faceData.lm, faceData.ao, out.lm, out.br);
    }

    // Light data for a grid-aligned quad that does not cover the whole face (IS_ALIGNED, IS_PARTIAL)
    private void applyAlignedPartialFace(AoNeighborInfo neighborInfo, ModelQuadView quad, int x, int y, int z, ModelQuadFacing dir, QuadLightData out) {
        this.applyPerVertex(neighborInfo, quad, x, y, z, dir, out, false, true);
    }

    // flags: !IS_ALIGNED, !IS_PARALLEL
    private void applyNonParallelFace(AoNeighborInfo neighborInfo, ModelQuadView quad, int x, int y, int z, ModelQuadFacing dir,
                                      QuadLightData out, boolean applyAoDepthBlending) {
        this.applyPerVertex(neighborInfo, quad, x, y, z, dir, out, true, applyAoDepthBlending);
    }

    // Shared per-vertex loop: bilinear corner weights per vertex, then either the face data straight (aligned) or depth-picked/blended (non-parallel)
    private void applyPerVertex(AoNeighborInfo neighborInfo, ModelQuadView quad, int x, int y, int z, ModelQuadFacing dir,
                                QuadLightData out, boolean useDepth, boolean applyAoDepthBlending) {
        float[] weights = this.weights;

        for (int i = 0; i < 4; i++) {
            // Clamp the vertex positions to the block's boundaries to prevent weird errors in lighting
            float cx = MathUtil.saturate(quad.getX(i));
            float cy = MathUtil.saturate(quad.getY(i));
            float cz = MathUtil.saturate(quad.getZ(i));

            neighborInfo.calculateCornerWeights(cx, cy, cz, weights);

            if (!useDepth) {
                this.applyAlignedPartialFaceVertex(x, y, z, dir, weights, true);
            } else {
                float depth = neighborInfo.getDepth(cx, cy, cz);

                if (applyAoDepthBlending) {
                    // Blend occlusion between the blocks beside this face and those above it based on how inset it is; fixes farmland and paths
                    this.applyInsetPartialFaceVertex(x, y, z, dir, depth, 1.0f - depth, weights);
                } else {
                    this.applyAlignedPartialFaceVertex(x, y, z, dir, weights, MathUtil.roughlyEqual(depth, 0.0F));
                }
            }

            out.br[i] = lastAo;
            out.lm[i] = getLightMapCoord(lastSl, lastBl);
        }
    }

    // Blends between the near and far face data for a vertex set back from the face
    private void applyInsetPartialFaceVertex(int x, int y, int z, ModelQuadFacing dir, float n1d, float n2d, float[] w) {
        // Avoid blending when the depth is close to one value or the other
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

        // Blend between the direct neighbors and above based on the passed weights
        this.lastAo = (n1.getBlendedShade(w) * n1d) + (n2.getBlendedShade(w) * n2d);
        this.lastSl = (n1.getBlendedSkyLight(w) * n1d) + (n2.getBlendedSkyLight(w) * n2d);
        this.lastBl = (n1.getBlendedBlockLight(w) * n1d) + (n2.getBlendedBlockLight(w) * n2d);
    }

    private static final float BLENDED_WEIGHT = 0.75f;
    private static final float MAX_WEIGHT = 1f - BLENDED_WEIGHT;

    // Non-axis-aligned quads: weights each vertex against the face nearest its normal
    private void applyIrregularFace(ModelQuadView quad, int x, int y, int z, QuadLightData out, boolean applyAoDepthBlending) {
        for (int i = 0; i < 4; i++) {
            // Clamp the vertex positions to the block's boundaries to prevent weird errors in lighting
            float cx = MathUtil.saturate(quad.getX(i));
            float cy = MathUtil.saturate(quad.getY(i));
            float cz = MathUtil.saturate(quad.getZ(i));

            int normal = quad.getForgeNormal(i);
            if (normal == 0) {
                normal = quad.getComputedFaceNormal();
            }

            float weightedAo = 0, weightedBl = 0, weightedSl = 0;
            float maxAo = 0, maxBl = 0, maxSl = 0;

            // Compute the values that each axis would contribute, then combine them
            for (int axis = 0; axis < 3; axis++) {
                // Unpack the desired normal component
                float projectedNormal = NormI8.unpackX(normal >> (axis * 8));

                // Skip any components that are zero (they will never contribute anything)
                if (projectedNormal == 0) {
                    continue;
                }

                var dir = ModelQuadFacing.AXES[axis].getFacing(projectedNormal > 0);

                var neighborInfo = AoNeighborInfo.get(dir);

                float[] weights = this.weights;
                neighborInfo.calculateCornerWeights(cx, cy, cz, weights);

                float depth = neighborInfo.getDepth(cx, cy, cz);

                if (applyAoDepthBlending) {
                    // Blend occlusion between the blocks beside this face and those above it based on how inset it is; fixes farmland and paths
                    this.applyInsetPartialFaceVertex(x, y, z, dir, depth, 1.0f - depth, weights);
                } else {
                    // Use inset data as soon as the face is even partially inset
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

    // A vertex on an axis-aligned face, optionally offset one block
    private void applyAlignedPartialFaceVertex(int x, int y, int z, ModelQuadFacing dir, float[] w, boolean offset) {
        AoFaceData faceData = this.getUnpackedFaceData(x, y, z, dir, offset);

        this.lastSl = faceData.getBlendedSkyLight(w);
        this.lastBl = faceData.getBlendedBlockLight(w);
        this.lastAo = faceData.getBlendedShade(w);
    }

    // Multiplies in vanilla's per-face diffuse
    private void applySidedBrightness(QuadLightData out, ModelQuadFacing face, boolean shade) {
        scaleBrightness(out.br, this.diffuseProvider.getDiffuse(face, shade));
    }

    // Per-vertex diffuse from the vertex normals, for irregular quads
    private void applySidedBrightnessFromNormals(QuadLightData out, ModelQuadView quad, boolean shade) {
        // TODO: consider calculating for vertex if mods actually change normals per-vertex
        int normal = quad.getModFaceNormal();
        scaleBrightness(out.br, this.diffuseProvider.getDiffuse(NormI8.unpackX(normal), NormI8.unpackY(normal), NormI8.unpackZ(normal), shade));
    }

    private static void scaleBrightness(float[] br, float brightness) {
        for (int i = 0; i < br.length; i++) {
            br[i] *= brightness;
        }
    }

    // the cached data for a given facing, calculating it if it has not been cached yet
    private AoFaceData getCachedFaceData(int x, int y, int z, ModelQuadFacing face, boolean offset) {
        AoFaceData data = this.cachedFaceData[offset ? face.ordinal() : face.ordinal() + 6];

        if (!data.hasLightData()) {
            data.initLightData(this.lightCache, x, y, z, face, offset);
        }

        return data;
    }

    // Cached face data with the sky/block split done, for the blended reads
    private AoFaceData getUnpackedFaceData(int x, int y, int z, ModelQuadFacing face, boolean offset) {
        AoFaceData data = this.getCachedFaceData(x, y, z, face, offset);

        if (!data.hasUnpackedLightData()) {
            data.unpackLightData();
        }

        return data;
    }

    // Loads face data for a new block position, reusing the cache across a block's faces
    private void updateCachedData(long key) {
        if (this.cachedPos != key) {
            for (AoFaceData data : this.cachedFaceData) {
                data.reset();
            }

            this.cachedPos = key;
        }
    }

    // the light map texture coordinate for the given block and sky light values
    private static int getLightMapCoord(float sl, float bl) {
        return (((int) sl & 0xFF) << 16) | ((int) bl & 0xFF);
    }

}