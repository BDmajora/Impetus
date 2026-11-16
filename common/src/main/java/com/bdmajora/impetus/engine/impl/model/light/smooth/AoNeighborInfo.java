package com.bdmajora.impetus.engine.impl.model.light.smooth;

import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;

// Neighbor layout of each face of a block for smooth lighting.
@SuppressWarnings("UnnecessaryLocalVariable")
enum AoNeighborInfo {
    POS_X(new ModelQuadFacing[] { ModelQuadFacing.NEG_Y, ModelQuadFacing.POS_Y, ModelQuadFacing.NEG_Z, ModelQuadFacing.POS_Z }) {
        // Calculates the corner weights of a point on this face.
        @Override
        public void calculateCornerWeights(float x, float y, float z, float[] out) {
            final float u = z;
            final float v = 1.0f - y;

            out[0] = v * u;
            out[1] = v * (1.0f - u);
            out[2] = (1.0f - v) * (1.0f - u);
            out[3] = (1.0f - v) * u;
        }

        // Maps the corner data to the vertex order of this face.
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

        // Returns the distance of a point from this face.
        @Override
        public float getDepth(float x, float y, float z) {
            return 1.0f - x;
        }
    },
    POS_Y(new ModelQuadFacing[] { ModelQuadFacing.POS_X, ModelQuadFacing.NEG_X, ModelQuadFacing.NEG_Z, ModelQuadFacing.POS_Z }) {
        // Calculates the corner weights of a point on this face.
        @Override
        public void calculateCornerWeights(float x, float y, float z, float[] out) {
            final float u = z;
            final float v = x;

            out[0] = v * u;
            out[1] = v * (1.0f - u);
            out[2] = (1.0f - v) * (1.0f - u);
            out[3] = (1.0f - v) * u;
        }

        // Maps the corner data to the vertex order of this face.
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

        // Returns the distance of a point from this face.
        @Override
        public float getDepth(float x, float y, float z) {
            return 1.0f - y;
        }
    },
    POS_Z(new ModelQuadFacing[] { ModelQuadFacing.NEG_X, ModelQuadFacing.POS_X, ModelQuadFacing.NEG_Y, ModelQuadFacing.POS_Y }) {
        // Calculates the corner weights of a point on this face.
        @Override
        public void calculateCornerWeights(float x, float y, float z, float[] out) {
            final float u = y;
            final float v = 1.0f - x;

            out[0] = u * v;
            out[1] = (1.0f - u) * v;
            out[2] = (1.0f - u) * (1.0f - v);
            out[3] = u * (1.0f - v);
        }

        // Maps the corner data to the vertex order of this face.
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

        // Returns the distance of a point from this face.
        @Override
        public float getDepth(float x, float y, float z) {
            return 1.0f - z;
        }
    },
    NEG_X(new ModelQuadFacing[] { ModelQuadFacing.POS_Y, ModelQuadFacing.NEG_Y, ModelQuadFacing.NEG_Z, ModelQuadFacing.POS_Z }) {
        // Calculates the corner weights of a point on this face.
        @Override
        public void calculateCornerWeights(float x, float y, float z, float[] out) {
            final float u = z;
            final float v = y;

            out[0] = v * u;
            out[1] = v * (1.0f - u);
            out[2] = (1.0f - v) * (1.0f - u);
            out[3] = (1.0f - v) * u;
        }

        // Maps the corner data to the vertex order of this face.
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

        // Returns the distance of a point from this face.
        @Override
        public float getDepth(float x, float y, float z) {
            return x;
        }
    },
    NEG_Y(new ModelQuadFacing[] { ModelQuadFacing.NEG_X, ModelQuadFacing.POS_X, ModelQuadFacing.NEG_Z, ModelQuadFacing.POS_Z }) {
        // Calculates the corner weights of a point on this face.
        @Override
        public void calculateCornerWeights(float x, float y, float z, float[] out) {
            final float u = z;
            final float v = 1.0f - x;

            out[0] = v * u;
            out[1] = v * (1.0f - u);
            out[2] = (1.0f - v) * (1.0f - u);
            out[3] = (1.0f - v) * u;
        }

        // Maps the corner data to the vertex order of this face.
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

        // Returns the distance of a point from this face.
        @Override
        public float getDepth(float x, float y, float z) {
            return y;
        }
    },
    NEG_Z(new ModelQuadFacing[] { ModelQuadFacing.POS_Y, ModelQuadFacing.NEG_Y, ModelQuadFacing.POS_X, ModelQuadFacing.NEG_X }) {
        // Calculates the corner weights of a point on this face.
        @Override
        public void calculateCornerWeights(float x, float y, float z, float[] out) {
            final float u = 1.0f - x;
            final float v = y;

            out[0] = v * u;
            out[1] = v * (1.0f - u);
            out[2] = (1.0f - v) * (1.0f - u);
            out[3] = (1.0f - v) * u;
        }

        // Maps the corner data to the vertex order of this face.
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

        // Returns the distance of a point from this face.
        @Override
        public float getDepth(float x, float y, float z) {
            return z;
        }
    };

    // Every constant by facing ordinal.
    private static final AoNeighborInfo[] VALUES = AoNeighborInfo.values();
    // Direction of each neighbor block of the face.
    public final ModelQuadFacing[] faces;

    // Creates the layout of a face.
    AoNeighborInfo(ModelQuadFacing[] directions) {
        this.faces = directions;
    }

    // Returns the layout of a direction and rejects an unassigned facing.
    public static AoNeighborInfo get(ModelQuadFacing direction) {
        if (!direction.isDirection()) {
            throw new IllegalArgumentException();
        }
        return VALUES[direction.ordinal()];
    }

    // Calculates the corner weights of a point on this face.
    public abstract void calculateCornerWeights(float x, float y, float z, float[] out);

    // Maps the corner data to the vertex order of this face.
    public abstract void mapCorners(int[] lm0, float[] ao0, int[] lm1, float[] ao1);

    // Returns the distance of a point from this face.
    public abstract float getDepth(float x, float y, float z);
}
