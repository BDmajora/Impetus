package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

// Shadow frustum that keeps the view frustum planes facing away from the light and extrudes its silhouette edges along the light direction
public class AdvancedShadowCullingFrustum implements Frustum {
    // Largest number of planes the frustum stores
    private static final int MAX_CLIPPING_PLANES = 13;
    // Result for a box entirely outside one plane
    public static final int OUTSIDE = -1;
    // Result for a box inside every plane
    public static final int INSIDE = -2;
    // Result for a box that crosses at least one plane
    public static final int INTERSECT = -3;

    // Distance box tested before the planes or null for no distance bound
    protected final ShadowBoxCuller boxCuller;
    // Four coefficients per plane where a point is inside when the dot product with the point and 1 is not negative
    private final float[] planes = new float[MAX_CLIPPING_PLANES * 4];
    // Unit vector from the origin toward the shadow light
    private final Vector3f shadowLightVectorFromOrigin;
    // Number of planes stored
    private int planeCount;

    // Builds the planes from the view projection and the light direction with an optional distance box
    public AdvancedShadowCullingFrustum(Matrix4fc modelViewProjection, Vector3f shadowLightVectorFromOrigin,
                                        ShadowBoxCuller boxCuller) {
        this.shadowLightVectorFromOrigin = shadowLightVectorFromOrigin;
        this.boxCuller = boxCuller;
        Vector4f[] basePlanes = new BaseClippingPlanes(modelViewProjection).getPlanes();
        boolean[] isBack = addBackPlanes(basePlanes);
        addEdgePlanes(basePlanes, isBack);
    }

    // Appends one plane and drops it once the store is full
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

    // Stores every plane whose normal points along or across the light and returns which ones point along it
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

    // Adds an extruded plane on every edge where a back plane meets a plane that is not a back plane
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

    // Adds the plane through the line where two planes meet with a normal perpendicular to the light
    private void addEdgePlane(Vector4f backPlane, Vector4f frontPlane, EdgeScratch scratch) {
        Vector3f backNormal = scratch.backNormal.set(backPlane.x(), backPlane.y(), backPlane.z());
        Vector3f frontNormal = scratch.frontNormal.set(frontPlane.x(), frontPlane.y(), frontPlane.z());
        // Direction of the line where the two planes meet
        Vector3f intersection = scratch.intersection.set(backNormal).cross(frontNormal);
        Vector3f edgeNormal = scratch.edgeNormal.set(intersection).cross(this.shadowLightVectorFromOrigin);
        // Point on the meeting line from the two plane offsets
        Vector3f ixb = scratch.ixb.set(intersection).cross(backNormal);
        Vector3f fxi = scratch.fxi.set(frontNormal).cross(intersection);
        ixb.mul(-frontPlane.w());
        fxi.mul(-backPlane.w());
        ixb.add(fxi);
        float lengthSquared = intersection.x() * intersection.x() + intersection.y() * intersection.y()
                + intersection.z() * intersection.z();
        // Skips parallel planes which have no meeting line
        if (lengthSquared == 0.0f) {
            return;
        }
        Vector3f point = ixb.mul(1.0f / lengthSquared);
        addPlane(edgeNormal.x(), edgeNormal.y(), edgeNormal.z(), -edgeNormal.dot(point));
    }

    // Classifies a camera relative box as OUTSIDE or INSIDE or INTERSECT against every plane
    protected int checkCornerVisibility(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        boolean inside = true;
        float[] planes = this.planes;
        int end = this.planeCount * 4;
        for (int base = 0; base < end; base += 4) {
            float a = planes[base];
            float b = planes[base + 1];
            float c = planes[base + 2];
            float w = planes[base + 3];
            // The corner farthest along the normal decides outside and the nearest corner decides inside
            float outsideBoundX = a < 0 ? minX : maxX;
            float outsideBoundY = b < 0 ? minY : maxY;
            float outsideBoundZ = c < 0 ? minZ : maxZ;
            if (a * outsideBoundX + b * outsideBoundY + c * outsideBoundZ >= -w) {
                inside &= a * (a < 0 ? maxX : minX) + b * (b < 0 ? maxY : minY) + c * (c < 0 ? maxZ : minZ) + w >= 0;
            } else {
                return OUTSIDE;
            }
        }
        return inside ? INSIDE : INTERSECT;
    }

    // Returns whether a camera relative box passes the distance box and is not outside any plane
    @Override
    public boolean testAab(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        if (this.boxCuller != null && !this.boxCuller.testAab(minX, minY, minZ, maxX, maxY, maxZ)) {
            return false;
        }
        return checkCornerVisibility(minX, minY, minZ, maxX, maxY, maxZ) != OUTSIDE;
    }

    // Vectors reused by every edge plane of one frustum
    private static final class EdgeScratch {
        // Normal of the back plane
        final Vector3f backNormal = new Vector3f();
        // Normal of the front plane
        final Vector3f frontNormal = new Vector3f();
        // Direction of the meeting line
        final Vector3f intersection = new Vector3f();
        // Normal of the edge plane
        final Vector3f edgeNormal = new Vector3f();
        // Meeting line direction crossed with the back normal
        final Vector3f ixb = new Vector3f();
        // Front normal crossed with the meeting line direction
        final Vector3f fxi = new Vector3f();
    }
}
