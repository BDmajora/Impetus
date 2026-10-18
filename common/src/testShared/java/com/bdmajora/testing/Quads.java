package com.bdmajora.testing;

import com.bdmajora.impetus.engine.api.util.ColorABGR;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.QuadSet;
import com.bdmajora.impetus.engine.impl.render.chunk.sorting.TranslucentQuadRecorder;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;

// Axis-aligned quads with known normals, and the arrangements that need each kind of translucency sort
public final class Quads {
    private Quads() {}

    // Flat on x, facing +X
    public static float[][] facingX(float x, float y0, float y1, float z0, float z1) {
        return new float[][]{{x, y0, z0}, {x, y1, z0}, {x, y1, z1}, {x, y0, z1}};
    }

    // Flat on y, facing +Y
    public static float[][] facingY(float y, float x0, float x1, float z0, float z1) {
        return new float[][]{{x0, y, z0}, {x0, y, z1}, {x1, y, z1}, {x1, y, z0}};
    }

    // Flat on z, facing +Z
    public static float[][] facingZ(float z, float x0, float x1, float y0, float y1) {
        return new float[][]{{x0, y0, z}, {x1, y0, z}, {x1, y1, z}, {x0, y1, z}};
    }

    // The same corners wound the other way round, so the quad faces the opposite direction
    public static float[][] flipped(float[][] quad) {
        return new float[][]{quad[3], quad[2], quad[1], quad[0]};
    }

    // Every corner on one point, so the quad has no normal and covers no pixels
    public static float[][] degenerate(float x, float y, float z) {
        return new float[][]{{x, y, z}, {x, y, z}, {x, y, z}, {x, y, z}};
    }

    // Each straddles the other's plane, which no single order satisfies, but they lie apart along z: a partition tree cuts at z = offset + 1
    public static float[][][] straddling(float offset) {
        return new float[][][]{
                facingY(offset, offset, offset + 3, offset, offset + 1),
                facingX(offset + 1, offset - 1, offset + 1, offset + 2, offset + 3)};
    }

    // Each quad is in front of the next round a cycle, no two touch and every axis-aligned cut splits one of them: only a per-frame distance sort works
    public static float[][][] pinwheel(float offset) {
        float o = offset;
        return new float[][][]{
                facingX(o, o - 1, o + 1, o + 0.5f, o + 2),
                facingY(o, o + 0.5f, o + 2, o - 1, o + 1),
                facingZ(o, o - 1, o + 1, o + 0.5f, o + 2)};
    }

    // The quads fed corner by corner through a recorder, as the mesher does
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

    // White, fully lit vertices at the quad's corners, ready to push into a mesh buffer
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
