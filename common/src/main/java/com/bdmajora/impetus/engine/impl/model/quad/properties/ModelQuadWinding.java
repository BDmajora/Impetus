package com.bdmajora.impetus.engine.impl.model.quad.properties;

// Orders of the vertices of the two triangles of a quad.
public enum ModelQuadWinding {
    CLOCKWISE(new int[] { 0, 1, 2, 2, 3, 0 }),
    COUNTERCLOCKWISE(new int[] { 0, 3, 2, 1, 0, 2 });

    // Vertex indices of the winding.
    private final int[] indices;

    // Creates a winding from its indices.
    ModelQuadWinding(int[] indices) {
        this.indices = indices;
    }

    // Returns a copy of the vertex indices.
    public int[] getIndices() {
        return this.indices.clone();
    }
}