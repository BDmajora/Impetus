package com.bdmajora.impetus.impl.extensions;

// Contract of the vertex format extension.
public interface VertexFormatExtension {
    // Returns the vertex size in ints.
    int impetus$intStride();

    // Returns the int index of the color.
    int impetus$colorIndex();

    // Returns the int index of the texture coordinate.
    int impetus$uvIndex();

    // Returns the int index of the lightmap coordinate.
    int impetus$lightIndex();

    // Returns the int index of the normal.
    int impetus$normalIndex();
}
