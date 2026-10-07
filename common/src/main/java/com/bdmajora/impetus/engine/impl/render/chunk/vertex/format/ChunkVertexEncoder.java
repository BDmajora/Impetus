package com.bdmajora.impetus.engine.impl.render.chunk.vertex.format;

import com.bdmajora.impetus.engine.impl.render.chunk.terrain.material.Material;

// Writes one terrain vertex into a native buffer in the active ChunkVertexType's layout; Umbra swaps in its own encoder when a pack loads, which is why Vertex carries fields the default never reads
public interface ChunkVertexEncoder {
    // Writes the vertex at ptr and returns the pointer just past it; sectionIndex identifies the section for formats packing a section-relative origin
    long write(long ptr, Material material, Vertex vertex, int sectionIndex);

    default boolean supportsBilinearCorrection() {
        return true;
    }

    class Vertex {
        public float x;
        public float y;
        public float z;
        public int color;
        public int rdhFactor;
        public float u;
        public float v;
        public int light;
        // The axis-snapped face normal vanilla would emit; Impetus's built-in shaders never read it but a replacement core shader might, so it is still packed
        public int vanillaNormal;
        // The quad's real geometric normal, which differs from vanillaNormal on anything non-axis-aligned and is what normal mapping needs
        public int trueNormal;

        // Umbra extended per-vertex data below (named by their GLSL attribute, see com.bdmajora.impetus.umbra.vertices); read only by Umbra encoders and computed only while a pack is loaded

        // mc_midTexCoord.x, the mean of the four vertex Us (matching Iris), NOT the sprite centre, which breaks the atlas basis Chocapic-derived packs reconstruct on sub-rect faces like torch caps
        public float midTexU;
        // mc_midTexCoord.y — the same for V, the mean of the four vertex Vs
        public float midTexV;
        // mc_Entity.x, the block id the shader sees: the pack's block.properties id when mapped, otherwise the raw block id
        public int blockId;
        // mc_Entity.y — the 1.12.2 OptiFine render type ordinal for this block
        public int blockRenderType;
        // mc_Entity.z — 1.12.2 block metadata, i.e. Block.getMetaFromState
        public int blockData;
        // at_tangent — packed tangent: xyz as signed bytes plus handedness in w, the same packing as a normal
        public int tangent;
        // at_midBlock.xyz, offset from this vertex to its block's CENTRE (times 64, stored as signed bytes); voxelization adds it to land on X.5 positions and keep light-volume lookups off cell boundaries
        public float midBlockX;
        public float midBlockY;
        public float midBlockZ;
        // at_midBlock.w — this block's light emission level, matching Iris's Sodium terrain extension
        public int blockEmission;

        // Four blank vertices for one quad, allocated once and refilled per quad; every field is default-valued and the caller sets all of them
        public static Vertex[] uninitializedQuad() {
            Vertex[] vertices = new Vertex[4];

            for (int i = 0; i < 4; i++) {
                vertices[i] = new Vertex();
            }

            return vertices;
        }

        // Debug only: position, packed colour and packed light, enough to spot a bad vertex in a breakpoint
        @Override
        public String toString() {
            return String.format("XYZ: (%.02f, %.02f, %.02f), C: %08x, L: %08x", x, y, z, color, light);
        }
    }
}
