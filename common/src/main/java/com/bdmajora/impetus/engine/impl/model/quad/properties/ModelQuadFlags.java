package com.bdmajora.impetus.engine.impl.model.quad.properties;

import com.bdmajora.impetus.engine.impl.common.util.MathUtil;
import com.bdmajora.impetus.engine.impl.model.quad.BakedQuadView;
import com.bdmajora.impetus.engine.impl.model.quad.ModelQuadView;
import com.bdmajora.impetus.engine.api.util.ColorABGR;

// Bit flags and the calculation of the properties of a quad.
public class ModelQuadFlags {
    // Flag of a quad that does not fully cover its face.
    public static final int IS_PARTIAL = 0b001;

    // Flag of a quad that is parallel to its light face.
    public static final int IS_PARALLEL = 0b010;

    // Flag of a quad on the block grid that is also parallel.
    public static final int IS_ALIGNED = 0b100;

    // Flag of a quad that is shaded by its light face.
    public static final int IS_VANILLA_SHADED = 0b1000;
    // Flag of a quad whose particle sprite is its only sprite.
    public static final int IS_TRUSTED_SPRITE = (1 << 4);
    // Flag of a quad that can use a cheaper render pass.
    public static final int IS_PASS_OPTIMIZABLE = (1 << 5);
    // Flag of a quad whose flags were calculated.
    public static final int IS_POPULATED = (1 << 31);

    // Returns whether the flags contain any bit of the mask.
    public static boolean contains(int flags, int mask) {
        return (flags & mask) != 0;
    }

    // Calculates the flags of a quad without earlier flags.
    public static int getQuadFlags(ModelQuadView quad, ModelQuadFacing face) {
        return getQuadFlags(quad, face, 0);
    }

    // Returns whether the vertices of a quad follow the baked order of its face.
    private static boolean canonicalVertexOrder(ModelQuadView quad, ModelQuadFacing face, float minX, float minY, float minZ,
                                                float maxX, float maxY, float maxZ) {
        return switch (face) {
            case NEG_Y -> MathUtil.roughlyEqual(quad.getX(0), minX) && MathUtil.roughlyEqual(quad.getY(0), minY) && MathUtil.roughlyEqual(quad.getZ(0), maxZ)
                    && MathUtil.roughlyEqual(quad.getX(1), minX) && MathUtil.roughlyEqual(quad.getY(1), minY) && MathUtil.roughlyEqual(quad.getZ(1), minZ)
                    && MathUtil.roughlyEqual(quad.getX(2), maxX) && MathUtil.roughlyEqual(quad.getY(2), minY) && MathUtil.roughlyEqual(quad.getZ(2), minZ)
                    && MathUtil.roughlyEqual(quad.getX(3), maxX) && MathUtil.roughlyEqual(quad.getY(3), minY) && MathUtil.roughlyEqual(quad.getZ(3), maxZ);
            case POS_Y -> MathUtil.roughlyEqual(quad.getX(0), minX) && MathUtil.roughlyEqual(quad.getY(0), maxY) && MathUtil.roughlyEqual(quad.getZ(0), minZ)
                    && MathUtil.roughlyEqual(quad.getX(1), minX) && MathUtil.roughlyEqual(quad.getY(1), maxY) && MathUtil.roughlyEqual(quad.getZ(1), maxZ)
                    && MathUtil.roughlyEqual(quad.getX(2), maxX) && MathUtil.roughlyEqual(quad.getY(2), maxY) && MathUtil.roughlyEqual(quad.getZ(2), maxZ)
                    && MathUtil.roughlyEqual(quad.getX(3), maxX) && MathUtil.roughlyEqual(quad.getY(3), maxY) && MathUtil.roughlyEqual(quad.getZ(3), minZ);
            case NEG_Z -> MathUtil.roughlyEqual(quad.getX(0), maxX) && MathUtil.roughlyEqual(quad.getY(0), maxY) && MathUtil.roughlyEqual(quad.getZ(0), minZ)
                    && MathUtil.roughlyEqual(quad.getX(1), maxX) && MathUtil.roughlyEqual(quad.getY(1), minY) && MathUtil.roughlyEqual(quad.getZ(1), minZ)
                    && MathUtil.roughlyEqual(quad.getX(2), minX) && MathUtil.roughlyEqual(quad.getY(2), minY) && MathUtil.roughlyEqual(quad.getZ(2), minZ)
                    && MathUtil.roughlyEqual(quad.getX(3), minX) && MathUtil.roughlyEqual(quad.getY(3), maxY) && MathUtil.roughlyEqual(quad.getZ(3), minZ);
            case POS_Z -> MathUtil.roughlyEqual(quad.getX(0), minX) && MathUtil.roughlyEqual(quad.getY(0), maxY) && MathUtil.roughlyEqual(quad.getZ(0), maxZ)
                    && MathUtil.roughlyEqual(quad.getX(1), minX) && MathUtil.roughlyEqual(quad.getY(1), minY) && MathUtil.roughlyEqual(quad.getZ(1), maxZ)
                    && MathUtil.roughlyEqual(quad.getX(2), maxX) && MathUtil.roughlyEqual(quad.getY(2), minY) && MathUtil.roughlyEqual(quad.getZ(2), maxZ)
                    && MathUtil.roughlyEqual(quad.getX(3), maxX) && MathUtil.roughlyEqual(quad.getY(3), maxY) && MathUtil.roughlyEqual(quad.getZ(3), maxZ);
            case NEG_X -> MathUtil.roughlyEqual(quad.getX(0), minX) && MathUtil.roughlyEqual(quad.getY(0), maxY) && MathUtil.roughlyEqual(quad.getZ(0), minZ)
                    && MathUtil.roughlyEqual(quad.getX(1), minX) && MathUtil.roughlyEqual(quad.getY(1), minY) && MathUtil.roughlyEqual(quad.getZ(1), minZ)
                    && MathUtil.roughlyEqual(quad.getX(2), minX) && MathUtil.roughlyEqual(quad.getY(2), minY) && MathUtil.roughlyEqual(quad.getZ(2), maxZ)
                    && MathUtil.roughlyEqual(quad.getX(3), minX) && MathUtil.roughlyEqual(quad.getY(3), maxY) && MathUtil.roughlyEqual(quad.getZ(3), maxZ);
            case POS_X -> MathUtil.roughlyEqual(quad.getX(0), maxX) && MathUtil.roughlyEqual(quad.getY(0), maxY) && MathUtil.roughlyEqual(quad.getZ(0), maxZ)
                    && MathUtil.roughlyEqual(quad.getX(1), maxX) && MathUtil.roughlyEqual(quad.getY(1), minY) && MathUtil.roughlyEqual(quad.getZ(1), maxZ)
                    && MathUtil.roughlyEqual(quad.getX(2), maxX) && MathUtil.roughlyEqual(quad.getY(2), minY) && MathUtil.roughlyEqual(quad.getZ(2), minZ)
                    && MathUtil.roughlyEqual(quad.getX(3), maxX) && MathUtil.roughlyEqual(quad.getY(3), maxY) && MathUtil.roughlyEqual(quad.getZ(3), minZ);
            case UNASSIGNED -> false;
        };
    }

    // Calculates the flags of a quad and keeps unrelated earlier flags and rejects an unassigned face.
    public static int getQuadFlags(ModelQuadView quad, ModelQuadFacing face, int existingFlags) {
        if (face == ModelQuadFacing.UNASSIGNED) {
            throw new IllegalArgumentException("Quad flags need an assigned face");
        }

        float minX = 32.0F;
        float minY = 32.0F;
        float minZ = 32.0F;

        float maxX = -32.0F;
        float maxY = -32.0F;
        float maxZ = -32.0F;

        int numVertices = 4;
        if (quad instanceof BakedQuadView bakedQuad) {
            numVertices = Math.min(numVertices, bakedQuad.getVerticesCount());
        }

        boolean degenerate = false, nonOpaqueColor = false;

        for (int i = 0; i < numVertices; ++i) {
            float x = quad.getX(i);
            float y = quad.getY(i);
            float z = quad.getZ(i);

            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);

            for (int j = 0; !degenerate && j < i; ++j) {
                float px = quad.getX(j);
                float py = quad.getY(j);
                float pz = quad.getZ(j);

                if (MathUtil.roughlyEqual(px, x) &&
                        MathUtil.roughlyEqual(py, y) &&
                        MathUtil.roughlyEqual(pz, z)) {
                    degenerate = true;
                    break;
                }
            }

            if (ColorABGR.unpackAlpha(quad.getColor(i)) != 255) {
                nonOpaqueColor = true;
            }
        }

        // Marks the quad partial when it has fewer than four vertices or does not cover the face or its vertices are out of order
        boolean partial = degenerate || numVertices < 4 || (switch (face.getAxis()) {
            case X -> minY >= 0.0001f || minZ >= 0.0001f || maxY <= 0.9999F || maxZ <= 0.9999F;
            case Y -> minX >= 0.0001f || minZ >= 0.0001f || maxX <= 0.9999F || maxZ <= 0.9999F;
            case Z -> minX >= 0.0001f || minY >= 0.0001f || maxX <= 0.9999F || maxY <= 0.9999F;
        }) || !canonicalVertexOrder(quad, face, minX, minY, minZ, maxX, maxY, maxZ);

        boolean parallel = switch(face.getAxis()) {
            case X -> minX == maxX;
            case Y -> minY == maxY;
            case Z -> minZ == maxZ;
        };

        boolean aligned = parallel && switch (face) {
            case NEG_Y -> minY < 0.0001f;
            case POS_Y -> maxY > 0.9999F;
            case NEG_Z -> minZ < 0.0001f;
            case POS_Z -> maxZ > 0.9999F;
            case NEG_X -> minX < 0.0001f;
            case POS_X -> maxX > 0.9999F;
            case UNASSIGNED -> throw new IllegalArgumentException();
        };

        int flags = existingFlags & ~(IS_PARTIAL | IS_PARALLEL | IS_ALIGNED);

        if (partial) {
            flags |= IS_PARTIAL;
        }

        if (parallel) {
            flags |= IS_PARALLEL;
        }

        if (aligned) {
            flags |= IS_ALIGNED;
        }

        if (!nonOpaqueColor && (flags & IS_TRUSTED_SPRITE) != 0) {
            flags |= IS_PASS_OPTIMIZABLE;
        }

        flags |= IS_POPULATED;

        return flags;
    }
}