package com.bdmajora.impetus.engine.impl.render.chunk.sorting;

import com.bdmajora.impetus.engine.impl.model.quad.properties.ModelQuadFacing;

// Quads of a section with their centers and bounds and normals.
public record QuadSet(int count, float[] centers, float[] bounds, float[] normals, float[] dots, byte[] facings) {
    // Tolerance of plane comparisons.
    static final float EPSILON = 1e-4f;
    // Tolerance of overlap comparisons.
    static final float OVERLAP_EPSILON = 0.008f;

    // Rejects a negative count or arrays whose lengths do not match the count.
    public QuadSet {
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        }

        if (centers.length != count * 3L) {
            throw new IllegalArgumentException("centers length must be count * 3");
        }

        if (bounds.length != count * 6L) {
            throw new IllegalArgumentException("bounds length must be count * 6");
        }

        if (normals.length != count * 3L) {
            throw new IllegalArgumentException("normals length must be count * 3");
        }

        if (dots.length != count) {
            throw new IllegalArgumentException("dots length must be count");
        }

        if (facings.length != count) {
            throw new IllegalArgumentException("facings length must be count");
        }
    }

    // Returns the smallest projection of the bounds of a quad onto a direction.
    public float minDot(int quad, float nx, float ny, float nz) {
        int boundOffset = quad * 6;

        return nx * (nx >= 0 ? bounds[boundOffset + 0] : bounds[boundOffset + 3])
                + ny * (ny >= 0 ? bounds[boundOffset + 1] : bounds[boundOffset + 4])
                + nz * (nz >= 0 ? bounds[boundOffset + 2] : bounds[boundOffset + 5]);
    }

    // Returns the largest projection of the bounds of a quad onto a direction.
    public float maxDot(int quad, float nx, float ny, float nz) {
        int boundOffset = quad * 6;

        return nx * (nx >= 0 ? bounds[boundOffset + 3] : bounds[boundOffset + 0])
                + ny * (ny >= 0 ? bounds[boundOffset + 4] : bounds[boundOffset + 1])
                + nz * (nz >= 0 ? bounds[boundOffset + 5] : bounds[boundOffset + 2]);
    }

    // Returns whether two quads lie in one plane.
    public boolean isCoplanar(int a, int b) {
        float normalDot = normalDot(a, b);

        if (Math.abs(Math.abs(normalDot) - 1.0f) > EPSILON) {
            return false;
        }

        float offsetB = normalDot < 0 ? -dots[b] : dots[b];
        return Math.abs(dots[a] - offsetB) < EPSILON;
    }

    // Returns the dot product of two normals.
    public float normalDot(int a, int b) {
        int ao = a * 3, bo = b * 3;

        return normals[ao] * normals[bo] + normals[ao + 1] * normals[bo + 1] + normals[ao + 2] * normals[bo + 2];
    }

    // Returns whether a quad has no normal.
    public boolean isDegenerate(int quad) {
        int normalOffset = quad * 3;

        return normals[normalOffset] == 0 && normals[normalOffset + 1] == 0 && normals[normalOffset + 2] == 0;
    }

    // Returns the x of the normal of a quad.
    public float normalX(int quad) {
        return normals[quad * 3];
    }

    // Returns the y of the normal of a quad.
    public float normalY(int quad) {
        return normals[quad * 3 + 1];
    }

    // Returns the z of the normal of a quad.
    public float normalZ(int quad) {
        return normals[quad * 3 + 2];
    }

    // Returns the facing of a quad.
    public ModelQuadFacing facing(int quad) {
        return ModelQuadFacing.VALUES[facings[quad]];
    }

    // Returns the bounds of every quad.
    public Bounds union() {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

        for (int quad = 0; quad < count; quad++) {
            int boundOffset = quad * 6;

            minX = Math.min(minX, bounds[boundOffset + 0]);
            minY = Math.min(minY, bounds[boundOffset + 1]);
            minZ = Math.min(minZ, bounds[boundOffset + 2]);
            maxX = Math.max(maxX, bounds[boundOffset + 3]);
            maxY = Math.max(maxY, bounds[boundOffset + 4]);
            maxZ = Math.max(maxZ, bounds[boundOffset + 5]);
        }

        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    // Box of minimum and maximum corners.
    public record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        // Returns the smallest projection of the box onto a direction.
        public float minDot(float nx, float ny, float nz) {
            return nx * (nx >= 0 ? minX : maxX) + ny * (ny >= 0 ? minY : maxY) + nz * (nz >= 0 ? minZ : maxZ);
        }

        // Returns the largest projection of the box onto a direction.
        public float maxDot(float nx, float ny, float nz) {
            return nx * (nx >= 0 ? maxX : minX) + ny * (ny >= 0 ? maxY : minY) + nz * (nz >= 0 ? maxZ : minZ);
        }
    }

    // Returns whether quad p lies behind quad q along both normals and the two are not coplanar.
    public boolean isSeenThrough(int p, int q) {
        if (isCoplanar(p, q)) return false;

        float qnx = normalX(q), qny = normalY(q), qnz = normalZ(q);
        float pnx = normalX(p), pny = normalY(p), pnz = normalZ(p);
        float pMin = minDot(p, qnx, qny, qnz), qMax = maxDot(q, pnx, pny, pnz);

        float pTol = maxDot(p, qnx, qny, qnz) - pMin > EPSILON ? OVERLAP_EPSILON : EPSILON;
        float qTol = qMax - minDot(q, pnx, pny, pnz) > EPSILON ? OVERLAP_EPSILON : EPSILON;

        return pMin < dots[q] - pTol && qMax > dots[p] + qTol;
    }

    // Returns the lower bound of a quad on an axis.
    public float boundsMin(int quad, int axis) {
        return bounds[quad * 6 + axis];
    }

    // Returns the upper bound of a quad on an axis.
    public float boundsMax(int quad, int axis) {
        return bounds[quad * 6 + 3 + axis];
    }
}
