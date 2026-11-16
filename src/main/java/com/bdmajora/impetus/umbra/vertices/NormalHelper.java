package com.bdmajora.impetus.umbra.vertices;

import com.bdmajora.impetus.engine.api.util.NormI8;
import org.joml.Vector3f;

// Packers of vertex normals.
public final class NormalHelper {
    // Prevents instantiation
    private NormalHelper() {
    }

    // Writes the normal of a quad.
    public static void computeFaceNormal(Vector3f saveTo,
                                         // First corner.
                                         float x0, float y0, float z0,
                                         // Third corner.
                                         float x2, float y2, float z2,
                                         // Second corner.
                                         float x1, float y1, float z1,
                                         // Fourth corner.
                                         float x3, float y3, float z3) {
        float dx0 = x2 - x0, dy0 = y2 - y0, dz0 = z2 - z0;
        float dx1 = x3 - x1, dy1 = y3 - y1, dz1 = z3 - z1;

        float nx = dy0 * dz1 - dz0 * dy1;
        float ny = dz0 * dx1 - dx0 * dz1;
        float nz = dx0 * dy1 - dy0 * dx1;

        float scale = rsqrt(nx * nx + ny * ny + nz * nz);
        saveTo.set(nx * scale, ny * scale, nz * scale);
    }

    // Returns the packed tangent of a triangle.
    public static int computeTangent(float normalX, float normalY, float normalZ,
                                     // First corner and texture coordinate.
                                     float x0, float y0, float z0, float u0, float v0,
                                     // Second corner and texture coordinate.
                                     float x1, float y1, float z1, float u1, float v1,
                                     // Third corner and texture coordinate.
                                     float x2, float y2, float z2, float u2, float v2) {
        float edge1x = x1 - x0;
        float edge1y = y1 - y0;
        float edge1z = z1 - z0;

        float edge2x = x2 - x0;
        float edge2y = y2 - y0;
        float edge2z = z2 - z0;

        float deltaU1 = u1 - u0;
        float deltaV1 = v1 - v0;
        float deltaU2 = u2 - u0;
        float deltaV2 = v2 - v0;

        float fdenom = deltaU1 * deltaV2 - deltaU2 * deltaV1;
        float f = fdenom == 0.0f ? 1.0f : 1.0f / fdenom;

        float tangentx = f * (deltaV2 * edge1x - deltaV1 * edge2x);
        float tangenty = f * (deltaV2 * edge1y - deltaV1 * edge2y);
        float tangentz = f * (deltaV2 * edge1z - deltaV1 * edge2z);
        float tcoeff = rsqrt(tangentx * tangentx + tangenty * tangenty + tangentz * tangentz);
        tangentx *= tcoeff;
        tangenty *= tcoeff;
        tangentz *= tcoeff;

        float bitangentx = f * (-deltaU2 * edge1x + deltaU1 * edge2x);
        float bitangenty = f * (-deltaU2 * edge1y + deltaU1 * edge2y);
        float bitangentz = f * (-deltaU2 * edge1z + deltaU1 * edge2z);
        float bitcoeff = rsqrt(bitangentx * bitangentx + bitangenty * bitangenty + bitangentz * bitangentz);
        bitangentx *= bitcoeff;
        bitangenty *= bitcoeff;
        bitangentz *= bitcoeff;

        float pbitangentx = tangenty * normalZ - tangentz * normalY;
        float pbitangenty = tangentz * normalX - tangentx * normalZ;
        float pbitangentz = tangentx * normalY - tangenty * normalX;

        float dot = (bitangentx * pbitangentx) + (bitangenty * pbitangenty) + (bitangentz * pbitangentz);
        float tangentW = dot < 0 ? -1.0f : 1.0f;

        return NormI8.pack(tangentx, tangenty, tangentz, tangentW);
    }

    // Returns the inverse square root.
    private static float rsqrt(float value) {
        if (value == 0.0f) {
            return 1.0f;
        }
        return (float) (1.0 / Math.sqrt(value));
    }
}
