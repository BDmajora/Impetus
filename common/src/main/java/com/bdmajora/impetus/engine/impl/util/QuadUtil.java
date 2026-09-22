package com.bdmajora.impetus.engine.impl.util;

import com.bdmajora.impetus.engine.api.util.NormI8;
import com.bdmajora.impetus.engine.impl.model.quad.ModelQuadView;
import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;
import com.bdmajora.impetus.engine.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.joml.Vector3f;

// Face-normal math shared by every quad representation: the normal is the normalised cross product of the two diagonals (v2 - v0) x (v3 - v1)
public class QuadUtil {
    // Closest axis-aligned facing to a normal, or UNASSIGNED when none dominates
    public static ModelQuadFacing findNormalFace(float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            return ModelQuadFacing.UNASSIGNED;
        }

        float maxDot = 0;
        ModelQuadFacing closestFace = null;

        for (ModelQuadFacing face : ModelQuadFacing.DIRECTIONS) {
            float dot = (x * face.getStepX()) + (y * face.getStepY()) + (z * face.getStepZ());

            if (dot > maxDot) {
                maxDot = dot;
                closestFace = face;
            }
        }

        if (closestFace != null && Math.abs(maxDot - 1.0f) < 1.0E-5F) {
            return closestFace;
        }

        return ModelQuadFacing.UNASSIGNED;
    }

    // Packed-normal form
    public static ModelQuadFacing findNormalFace(int normal) {
        return findNormalFace(NormI8.unpackX(normal), NormI8.unpackY(normal), NormI8.unpackZ(normal));
    }

    // Writes the unit face normal of the quad with corners (x0..z3) into result; a degenerate quad leaves the zero vector
    public static void faceNormal(float x0, float y0, float z0, float x1, float y1, float z1,
                                  float x2, float y2, float z2, float x3, float y3, float z3, Vector3f result) {
        final float dx0 = x2 - x0, dy0 = y2 - y0, dz0 = z2 - z0;
        final float dx1 = x3 - x1, dy1 = y3 - y1, dz1 = z3 - z1;

        float normX = dy0 * dz1 - dz0 * dy1;
        float normY = dz0 * dx1 - dx0 * dz1;
        float normZ = dx0 * dy1 - dy0 * dx1;

        float l = (float) Math.sqrt(normX * normX + normY * normY + normZ * normZ);
        if (l != 0) {
            normX /= l;
            normY /= l;
            normZ /= l;
        }

        result.set(normX, normY, normZ);
    }

    // Same math packed straight to NormI8; kept allocation-free rather than routed through a scratch vector because it runs once per quad on the chunk-build workers
    public static int packedFaceNormal(float x0, float y0, float z0, float x1, float y1, float z1,
                                       float x2, float y2, float z2, float x3, float y3, float z3) {
        final float dx0 = x2 - x0, dy0 = y2 - y0, dz0 = z2 - z0;
        final float dx1 = x3 - x1, dy1 = y3 - y1, dz1 = z3 - z1;

        float normX = dy0 * dz1 - dz0 * dy1;
        float normY = dz0 * dx1 - dx0 * dz1;
        float normZ = dx0 * dy1 - dy0 * dx1;

        float l = (float) Math.sqrt(normX * normX + normY * normY + normZ * normZ);
        if (l != 0) {
            normX /= l;
            normY /= l;
            normZ /= l;
        }

        return NormI8.pack(normX, normY, normZ);
    }

    // Packed face normal of an encoder quad
    public static int calculateNormal(ChunkVertexEncoder.Vertex[] quad) {
        ChunkVertexEncoder.Vertex q0 = quad[0], q1 = quad[1], q2 = quad[2], q3 = quad[3];
        return packedFaceNormal(q0.x, q0.y, q0.z, q1.x, q1.y, q1.z, q2.x, q2.y, q2.z, q3.x, q3.y, q3.z);
    }

    // Packed face normal of a model quad
    public static int calculateNormal(ModelQuadView quad) {
        return packedFaceNormal(quad.getX(0), quad.getY(0), quad.getZ(0),
                quad.getX(1), quad.getY(1), quad.getZ(1),
                quad.getX(2), quad.getY(2), quad.getZ(2),
                quad.getX(3), quad.getY(3), quad.getZ(3));
    }
}
