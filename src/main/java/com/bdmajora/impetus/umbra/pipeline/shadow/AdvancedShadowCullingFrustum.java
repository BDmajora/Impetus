package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

// Shadow frustum that keeps only the camera frustum planes facing away from the light and adds edge planes along it.
public class AdvancedShadowCullingFrustum implements Frustum {
    // Largest number of clipping planes.
    private static final int MAX_CLIPPING_PLANES = 13;
    // Result of a box outside the frustum.
    public static final int OUTSIDE = -1;
    // Result of a box fully inside the frustum.
    public static final int INSIDE = -2;
    // Result of a box that crosses a plane.
    public static final int INTERSECT = -3;

    // Distance culler of shadow boxes or null.
    protected final ShadowBoxCuller boxCuller;
    // Plane coefficients in groups of four.
    private final float[] planes = new float[MAX_CLIPPING_PLANES * 4];
    // Direction of the light.
    private final Vector3f shadowLightVectorFromOrigin;
    // Number of planes.
    private int planeCount;

    // Creates the frustum from the camera matrix and the light direction.
    public AdvancedShadowCullingFrustum(Matrix4fc modelViewProjection, Vector3f shadowLightVectorFromOrigin,
                                        ShadowBoxCuller boxCuller) {
        this.shadowLightVectorFromOrigin = shadowLightVectorFromOrigin;
        this.boxCuller = boxCuller;
        Vector4f[] basePlanes = new BaseClippingPlanes(modelViewProjection).getPlanes();
        boolean[] isBack = addBackPlanes(basePlanes);
        addEdgePlanes(basePlanes, isBack);
    }

    // Adds a plane while the plane limit allows.
    private void addPlane(float a, float b, float c, float w) {
        if (this.planeCount >= MAX_CLIPPING_PLANES) {
            return;
        }
        int base = this.planeCount * 4;
        this.planes[base] = a;
        this.planes[base + 1] = b;
        this.planes[base + 2] = c;
        this.planes[base + 3] = w;
        this.planeCount++;
    }

    // Adds the planes that face away from the light and returns which planes those are.
    private boolean[] addBackPlanes(Vector4f[] basePlanes) {
        boolean[] isBack = new boolean[basePlanes.length];
        for (int planeIndex = 0; planeIndex < basePlanes.length; planeIndex++) {
            Vector4f plane = basePlanes[planeIndex];
            float dot = this.shadowLightVectorFromOrigin.dot(plane.x, plane.y, plane.z);
            boolean back = dot > 0.0f;
            isBack[planeIndex] = back;
            if (back || dot == 0.0f) {
                addPlane(plane.x, plane.y, plane.z, plane.w);
            }
        }
        return isBack;
    }

    // Adds a plane along the light for every edge between a back and a front plane.
    private void addEdgePlanes(Vector4f[] basePlanes, boolean[] isBack) {
        EdgeScratch scratch = new EdgeScratch();
        for (int planeIndex = 0; planeIndex < basePlanes.length; planeIndex++) {
            if (!isBack[planeIndex]) {
                continue;
            }
            Vector4f plane = basePlanes[planeIndex];
            NeighboringPlaneSet neighbors = NeighboringPlaneSet.forPlane(planeIndex);
            if (!isBack[neighbors.plane0()]) {
                addEdgePlane(plane, basePlanes[neighbors.plane0()], scratch);
            }
            if (!isBack[neighbors.plane1()]) {
                addEdgePlane(plane, basePlanes[neighbors.plane1()], scratch);
            }
            if (!isBack[neighbors.plane2()]) {
                addEdgePlane(plane, basePlanes[neighbors.plane2()], scratch);
            }
            if (!isBack[neighbors.plane3()]) {
                addEdgePlane(plane, basePlanes[neighbors.plane3()], scratch);
            }
        }
    }

    // Adds the plane through the edge of two planes that contains the light direction.
    private void addEdgePlane(Vector4f backPlane, Vector4f frontPlane, EdgeScratch scratch) {
        Vector3f backNormal = scratch.backNormal.set(backPlane.x(), backPlane.y(), backPlane.z());
        Vector3f frontNormal = scratch.frontNormal.set(frontPlane.x(), frontPlane.y(), frontPlane.z());
        Vector3f intersection = scratch.intersection.set(backNormal).cross(frontNormal);
        Vector3f edgeNormal = scratch.edgeNormal.set(intersection).cross(this.shadowLightVectorFromOrigin);
        Vector3f ixb = scratch.ixb.set(intersection).cross(backNormal);
        Vector3f fxi = scratch.fxi.set(frontNormal).cross(intersection);
        ixb.mul(-frontPlane.w());
        fxi.mul(-backPlane.w());
        ixb.add(fxi);
        float lengthSquared = intersection.x() * intersection.x() + intersection.y() * intersection.y()
                + intersection.z() * intersection.z();
        if (lengthSquared == 0.0f) {
            return;
        }
        Vector3f point = ixb.mul(1.0f / lengthSquared);
        addPlane(edgeNormal.x(), edgeNormal.y(), edgeNormal.z(), -edgeNormal.dot(point));
    }

    // Returns whether a box is outside or inside or crosses the planes.
    protected int checkCornerVisibility(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        boolean inside = true;
        float[] planes = this.planes;
        int end = this.planeCount * 4;
        for (int base = 0; base < end; base += 4) {
            float a = planes[base];
            float b = planes[base + 1];
            float c = planes[base + 2];
            float w = planes[base + 3];
            float outsideBoundX = a < 0 ? minX : maxX;
            float outsideBoundY = b < 0 ? minY : maxY;
            float outsideBoundZ = c < 0 ? minZ : maxZ;
            if (!(a * outsideBoundX + b * outsideBoundY + c * outsideBoundZ >= -w)) {
                return OUTSIDE;
            }
            // Tests the inner corner only while the box may still be fully inside
            if (inside) {
                inside = a * (a < 0 ? maxX : minX) + b * (b < 0 ? maxY : minY) + c * (c < 0 ? maxZ : minZ) + w >= 0;
            }
        }
        return inside ? INSIDE : INTERSECT;
    }

    // Returns whether a box passes the box culler and the planes.
    @Override
    public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        if (this.boxCuller != null && !this.boxCuller.testAab(minX, minY, minZ, maxX, maxY, maxZ)) {
            return false;
        }
        return checkCornerVisibility(minX, minY, minZ, maxX, maxY, maxZ) != OUTSIDE;
    }

    // Scratch vectors of the edge plane calculation.
    private static final class EdgeScratch {
        // Normal of the back plane.
        final Vector3f backNormal = new Vector3f();
        // Normal of the front plane.
        final Vector3f frontNormal = new Vector3f();
        // Direction of the edge.
        final Vector3f intersection = new Vector3f();
        // Normal of the edge plane.
        final Vector3f edgeNormal = new Vector3f();
        // Cross product of the edge and the back normal.
        final Vector3f ixb = new Vector3f();
        // Cross product of the front normal and the edge.
        final Vector3f fxi = new Vector3f();
    }
}
