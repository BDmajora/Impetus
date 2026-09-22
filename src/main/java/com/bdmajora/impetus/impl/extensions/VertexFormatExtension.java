package com.bdmajora.impetus.impl.extensions;

// Element positions of a vertex format as int-array indices, cached on the format so the per-vertex quad readers do one multiply-add instead of unboxing the format's offset lists
public interface VertexFormatExtension {
    // Ints per vertex
    int impetus$intStride();

    // Int index of the colour element within a vertex, or -1 when the format has none
    int impetus$colorIndex();

    // Int index of the first UV set (block atlas)
    int impetus$uvIndex();

    // Int index of the second UV set (lightmap), or -1 when absent
    int impetus$lightIndex();

    // Int index of the normal element, or -1 when absent
    int impetus$normalIndex();
}
