package com.bdmajora.impetus.engine.impl.model.quad.properties;

// Vertex order of a model quad.
public enum ModelQuadOrientation {
    NORMAL(new int[] { 0, 1, 2, 3 }),
    FLIP(new int[] { 1, 2, 3, 0 });

    // Vertex indices of the orientation.
    private final int[] indices;

    // Creates an orientation from its indices.
    ModelQuadOrientation(int[] indices) {
        this.indices = indices;
    }

    // Returns the index of a vertex in this orientation.
    public int getVertexIndex(int idx) {
        return this.indices[idx];
    }

    // Returns the orientation that splits the quad along its brighter diagonal.
    public static ModelQuadOrientation orientByBrightness(float[] brightnesses, int[] lightmaps) {
        // Compares the brightness of both diagonals
        float br02 = brightnesses[0] + brightnesses[2];
        float br13 = brightnesses[1] + brightnesses[3];
        if (br02 > br13) {
            return NORMAL;
        } else if (br02 < br13) {
            return FLIP;
        }

        // Compares the lightmaps of both diagonals
        long lm02 = (long) lightmaps[0] + lightmaps[2];
        long lm13 = (long) lightmaps[1] + lightmaps[3];
        if (lm02 <= lm13) {
            return NORMAL;
        } else {
            return FLIP;
        }
    }
}
