package com.bdmajora.impetus.engine.impl.model.light.smooth;

import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;

// Neighbour information for each face of a block, used during smooth lighting to calculate each corner's occlusion
@SuppressWarnings("UnnecessaryLocalVariable")
enum AoNeighborInfo {
    POS_X(new ModelQuadFacing[] { ModelQuadFacing.NEG_Y, ModelQuadFacing.POS_Y, ModelQuadFacing.NEG_Z, ModelQuadFacing.POS_Z }) {
        // Bilinear weights of the four corners for a point on this face
        @Override
        public void calculateCornerWeights(float x, float y, float z, float[] out) {
            final float u = z;
            final float v = 1.0f - y;

            out[0] = v * u;
            out[1] = v * (1.0f - u);
            out[2] = (1.0f - v) * (1.0f - u);
            out[3] = (1.0f - v) * u;
        }

        // Reorders corner data from neighbour order to this face's vertex order
        @Override
        public void mapCorners(int[] lm0, float[] ao0, int[] lm1, float[] ao1) {
            lm1[1] = lm0[0];
            lm1[2] = lm0[1];
            lm1[3] = lm0[2];
            lm1[0] = lm0[3];

            ao1[1] = ao0[0];
            ao1[2] = ao0[1];
            ao1[3] = ao0[2];
            ao1[0] = ao0[3];
        }

        // Distance from this face along its axis, for depth blending
        @Override
        public float getDepth(float x, float y, float z) {
            return 1.0f - x;
        }
    },
    POS_Y(new ModelQuadFacing[] { ModelQuadFacing.POS_X, ModelQuadFacing.NEG_X, ModelQuadFacing.NEG_Z, ModelQuadFacing.POS_Z }) {
        // Bilinear weights of the four corners for a point on this face
        @Override
        public void calculateCornerWeights(float x, float y, float z, float[] out) {
            final float u = z;
            final float v = x;

            out[0] = v * u;
            out[1] = v * (1.0f - u);
            out[2] = (1.0f - v) * (1.0f - u);
            out[3] = (1.0f - v) * u;
        }

        // Reorders corner data from neighbour order to this face's vertex order
        @Override
        public void mapCorners(int[] lm0, float[] ao0, int[] lm1, float[] ao1) {
            lm1[2] = lm0[0];
            lm1[3] = lm0[1];
            lm1[0] = lm0[2];
            lm1[1] = lm0[3];

            ao1[2] = ao0[0];
            ao1[3] = ao0[1];
            ao1[0] = ao0[2];
            ao1[1] = ao0[3];
        }

        // Distance from this face along its axis, for depth blending
        @Override
        public float getDepth(float x, float y, float z) {
            return 1.0f - y;
        }
    },
    POS_Z(new ModelQuadFacing[] { ModelQuadFacing.NEG_X, ModelQuadFacing.POS_X, ModelQuadFacing.NEG_Y, ModelQuadFacing.POS_Y }) {
        // Bilinear weights of the four corners for a point on this face
        @Override
        public void calculateCornerWeights(float x, float y, float z, float[] out) {
            final float u = y;
            final float v = 1.0f - x;

            out[0] = u * v;
            out[1] = (1.0f - u) * v;
            out[2] = (1.0f - u) * (1.0f - v);
            out[3] = u * (1.0f - v);
        }

        // Reorders corner data from neighbour order to this face's vertex order
        @Override
        public void mapCorners(int[] lm0, float[] ao0, int[] lm1, float[] ao1) {
            lm1[0] = lm0[0];
            lm1[1] = lm0[1];
            lm1[2] = lm0[2];
            lm1[3] = lm0[3];

            ao1[0] = ao0[0];
            ao1[1] = ao0[1];
            ao1[2] = ao0[2];
            ao1[3] = ao0[3];
        }

        // Distance from this face along its axis, for depth blending
        @Override
        public float getDepth(float x, float y, float z) {
            return 1.0f - z;
        }
    },
    NEG_X(new ModelQuadFacing[] { ModelQuadFacing.POS_Y, ModelQuadFacing.NEG_Y, ModelQuadFacing.NEG_Z, ModelQuadFacing.POS_Z }) {
        // Bilinear weights of the four corners for a point on this face
        @Override
        public void calculateCornerWeights(float x, float y, float z, float[] out) {
            final float u = z;
            final float v = y;

            out[0] = v * u;
            out[1] = v * (1.0f - u);
            out[2] = (1.0f - v) * (1.0f - u);
            out[3] = (1.0f - v) * u;
        }

        // Reorders corner data from neighbour order to this face's vertex order
        @Override
        public void mapCorners(int[] lm0, float[] ao0, int[] lm1, float[] ao1) {
            lm1[3] = lm0[0];
            lm1[0] = lm0[1];
            lm1[1] = lm0[2];
            lm1[2] = lm0[3];

            ao1[3] = ao0[0];
            ao1[0] = ao0[1];
            ao1[1] = ao0[2];
            ao1[2] = ao0[3];
        }

        // Distance from this face along its axis, for depth blending
        @Override
        public float getDepth(float x, float y, float z) {
            return x;
        }
    },
    NEG_Y(new ModelQuadFacing[] { ModelQuadFacing.NEG_X, ModelQuadFacing.POS_X, ModelQuadFacing.NEG_Z, ModelQuadFacing.POS_Z }) {
        // Bilinear weights of the four corners for a point on this face
        @Override
        public void calculateCornerWeights(float x, float y, float z, float[] out) {
            final float u = z;
            final float v = 1.0f - x;

            out[0] = v * u;
            out[1] = v * (1.0f - u);
            out[2] = (1.0f - v) * (1.0f - u);
            out[3] = (1.0f - v) * u;
        }

        // Reorders corner data from neighbour order to this face's vertex order
        @Override
        public void mapCorners(int[] lm0, float[] ao0, int[] lm1, float[] ao1) {
            lm1[0] = lm0[0];
            lm1[1] = lm0[1];
            lm1[2] = lm0[2];
            lm1[3] = lm0[3];

            ao1[0] = ao0[0];
            ao1[1] = ao0[1];
            ao1[2] = ao0[2];
            ao1[3] = ao0[3];
        }

        // Distance from this face along its axis, for depth blending
        @Override
        public float getDepth(float x, float y, float z) {
            return y;
        }
    },
    NEG_Z(new ModelQuadFacing[] { ModelQuadFacing.POS_Y, ModelQuadFacing.NEG_Y, ModelQuadFacing.POS_X, ModelQuadFacing.NEG_X }) {
        // Bilinear weights of the four corners for a point on this face
        @Override
        public void calculateCornerWeights(float x, float y, float z, float[] out) {
            final float u = 1.0f - x;
            final float v = y;

            out[0] = v * u;
            out[1] = v * (1.0f - u);
            out[2] = (1.0f - v) * (1.0f - u);
            out[3] = (1.0f - v) * u;
        }

        // Reorders corner data from neighbour order to this face's vertex order
        @Override
        public void mapCorners(int[] lm0, float[] ao0, int[] lm1, float[] ao1) {
            lm1[3] = lm0[0];
            lm1[0] = lm0[1];
            lm1[1] = lm0[2];
            lm1[2] = lm0[3];

            ao1[3] = ao0[0];
            ao1[0] = ao0[1];
            ao1[1] = ao0[2];
            ao1[2] = ao0[3];
        }

        // Distance from this face along its axis, for depth blending
        @Override
        public float getDepth(float x, float y, float z) {
            return z;
        }
    };

    private static final AoNeighborInfo[] VALUES = AoNeighborInfo.values();
    // Direction of each corner block from this face, reached by offsetting the origin block's position by the direction vector
    public final ModelQuadFacing[] faces;
    AoNeighborInfo(ModelQuadFacing[] directions) {
        this.faces = directions;
    }

    // the AoNeighborInfo corresponding to the given direction
    public static AoNeighborInfo get(ModelQuadFacing direction) {
        if (!direction.isDirection()) {
            throw new IllegalArgumentException();
        }
        return VALUES[direction.ordinal()];
    }

    // Weight of each corner's contribution to the darkening of the vertex at x/y/z, as a function of distance from the vertex to the corner block
    public abstract void calculateCornerWeights(float x, float y, float z, float[] out);

    // Re-orients the lightmap (lm0) and occlusion (ao0) arrays from AoFaceData onto this facing's corners, writing lm1/ao1
    public abstract void mapCorners(int[] lm0, float[] ao0, int[] lm1, float[] ao1);

    // Depth (inset) of the vertex at x/y/z into this facing, used to decide how much shadow the direct neighbours contribute
    public abstract float getDepth(float x, float y, float z);
}
