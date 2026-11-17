package com.bdmajora.testing;

import com.bdmajora.impetus.engine.api.util.ColorABGR;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.QuadSet;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.TranslucentQuadRecorder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;

// Quads with known normals.
public final class Quads {
    private Quads() {}

    // Faces +X.
    public static float[][] facingX(float x, float y0, float y1, float z0, float z1) {
        return new float[][]{{x, y0, z0}, {x, y1, z0}, {x, y1, z1}, {x, y0, z1}};
    }

    // Faces +Y.
    public static float[][] facingY(float y, float x0, float x1, float z0, float z1) {
        return new float[][]{{x0, y, z0}, {x0, y, z1}, {x1, y, z1}, {x1, y, z0}};
    }

    // Faces +Z.
    public static float[][] facingZ(float z, float x0, float x1, float y0, float y1) {
        return new float[][]{{x0, y0, z}, {x1, y0, z}, {x1, y1, z}, {x0, y1, z}};
    }

    // Flips the quad.
    public static float[][] flipped(float[][] quad) {
        return new float[][]{quad[3], quad[2], quad[1], quad[0]};
    }

    // Degenerate quad.
    public static float[][] degenerate(float x, float y, float z) {
        return new float[][]{{x, y, z}, {x, y, z}, {x, y, z}, {x, y, z}};
    }

    // Straddling quads.
    public static float[][][] straddling(float offset) {
        return new float[][][]{
                facingY(offset, offset, offset + 3, offset, offset + 1),
                facingX(offset + 1, offset - 1, offset + 1, offset + 2, offset + 3)};
    }

    // Cyclic quads.
    public static float[][][] pinwheel(float offset) {
        float o = offset;
        return new float[][][]{
                facingX(o, o - 1, o + 1, o + 0.5f, o + 2),
                facingY(o, o + 0.5f, o + 2, o - 1, o + 1),
                facingZ(o, o - 1, o + 1, o + 0.5f, o + 2)};
    }

    // Feeds the quads through a recorder.
    public static TranslucentQuadRecorder record(float[][]... quads) {
        TranslucentQuadRecorder recorder = new TranslucentQuadRecorder();
        for (float[][] quad : quads) {
            for (ChunkVertexEncoder.Vertex vertex : vertices(quad)) {
                recorder.capture(vertex);
            }
        }
        return recorder;
    }

    public static QuadSet set(float[][]... quads) {
        return record(quads).toQuadSet();
    }

    // Returns lit vertices.
    public static ChunkVertexEncoder.Vertex[] vertices(float[][] quad) {
        ChunkVertexEncoder.Vertex[] vertices = ChunkVertexEncoder.Vertex.uninitializedQuad();
        for (int i = 0; i < 4; i++) {
            vertices[i].x = quad[i][0];
            vertices[i].y = quad[i][1];
            vertices[i].z = quad[i][2];
            vertices[i].color = ColorABGR.pack(255, 255, 255, 255);
            vertices[i].light = 0x00F000F0;
        }
        return vertices;
    }
}
