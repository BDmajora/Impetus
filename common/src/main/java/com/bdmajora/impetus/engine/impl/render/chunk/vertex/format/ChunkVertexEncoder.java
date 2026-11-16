package com.bdmajora.impetus.engine.impl.render.chunk.vertex.format;

import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;

// Writes one terrain vertex into a native buffer.
public interface ChunkVertexEncoder {
    // Writes a vertex at an address and returns the address after it.
    long write(long ptr, Material material, Vertex vertex, int sectionIndex);

    // Returns whether the encoder supports bilinear correction.
    default boolean supportsBilinearCorrection() {
        return true;
    }

    // Vertex data before encoding.
    class Vertex {
        // Position of the vertex.
        public float x;
        public float y;
        public float z;
        // Packed color.
        public int color;
        // Factor of the render distance haze.
        public int rdhFactor;
        // Texture coordinates.
        public float u;
        public float v;
        // Packed light.
        public int light;
        // Normal snapped to an axis.
        public int vanillaNormal;
        // Geometric normal of the quad.
        public int trueNormal;

        // Mean texture coordinates of the quad.
        public float midTexU;
        public float midTexV;
        // Id of the block.
        public int blockId;
        // Render type of the block.
        public int blockRenderType;
        // Metadata of the block.
        public int blockData;
        // Packed tangent.
        public int tangent;
        // Offset from the vertex to the center of the block.
        public float midBlockX;
        public float midBlockY;
        public float midBlockZ;
        // Light emission of the block.
        public int blockEmission;

        // Returns four vertices for one quad.
        public static Vertex[] uninitializedQuad() {
            Vertex[] vertices = new Vertex[4];

            for (int i = 0; i < 4; i++) {
                vertices[i] = new Vertex();
            }

            return vertices;
        }

        // Returns the position and color and light as text.
        @Override
        public String toString() {
            return String.format("XYZ: (%.02f, %.02f, %.02f), C: %08x, L: %08x", x, y, z, color, light);
        }
    }
}
