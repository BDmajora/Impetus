package com.bdmajora.impetus.engine.impl.model.light.data;

// Light values of the four vertices of a quad.
public class QuadLightData {
    // Brightness of each vertex between 0 and 1.
    public final float[] br = new float[4];

    // Packed lightmap coordinates of each vertex.
    public final int[] lm = new int[4];
}
