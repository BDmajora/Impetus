package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.pipeline.ShadowContentSettings;
import com.bdmajora.impetus.umbra.uniforms.CelestialUniforms;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

// Covers the shadow culling frustums and their factory
class ShadowCullingTest {
    // Camera at the origin looking down negative Z with a 90 degree field of view out to 256 blocks
    private static final Matrix4f PROJECTION = new Matrix4f().perspective((float) Math.toRadians(90), 1.0F, 0.05F, 256.0F);

    // Installs a client without a world
    @BeforeEach
    void noWorld() {
        Mc.client();
    }

    // Resets the sun path tilt
    @AfterEach
    void resetSunPath() {
        CelestialUniforms.setSunPathRotation(0.0f);
    }

    // Checks that the box culler accepts boxes overlapping the cube and rejects boxes past each face
    @Test
    void theBoxCullerIsACubeAroundTheCamera() {
        ShadowBoxCuller box = new ShadowBoxCuller(16);
        assertTrue(box.testAab(-1, -1, -1, 1, 1, 1));
        assertFalse(box.testAab(-40, 0, 0, -20, 1, 1));
        assertFalse(box.testAab(20, 0, 0, 40, 1, 1));
        assertFalse(box.testAab(0, -40, 0, 1, -20, 1));
        assertFalse(box.testAab(0, 20, 0, 1, 40, 1));
        assertFalse(box.testAab(0, 0, -40, 1, 1, -20));
        assertFalse(box.testAab(0, 0, 20, 1, 1, 40));
    }

    // Checks that touching boxes and NaN bounds count as overlapping and negative or non finite sizes behave as plain comparisons
    @Test
    void theBoxCullerKeepsTouchingBoxesAndNaN() {
        ShadowBoxCuller box = new ShadowBoxCuller(16);
        assertTrue(box.testAab(-20, 0, 0, -16, 1, 1));
        assertTrue(box.testAab(16, 0, 0, 20, 1, 1));
        assertTrue(box.testAab(0, 0, 16, 1, 1, 20));
        assertFalse(box.testAab(16.001f, 0, 0, 20, 1, 1));
        assertTrue(box.testAab(Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN, Float.NaN));
        assertFalse(new ShadowBoxCuller(-1).testAab(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f));
        assertTrue(new ShadowBoxCuller(0).testAab(0, 0, 0, 0, 0, 0));
        assertTrue(new ShadowBoxCuller(Float.POSITIVE_INFINITY).testAab(-Float.MAX_VALUE, 0, 0, -Float.MAX_VALUE, 1, 1));
        assertTrue(new ShadowBoxCuller(Float.NaN).testAab(1e30f, 1e30f, 1e30f, 1e30f, 1e30f, 1e30f));
    }

    // Checks that neighbours never include the plane itself or its opposite and opposite planes share a set
    @Test
    void neighbouringPlanesShareAnAxis() {
        for (int plane = 0; plane < 6; plane++) {
            NeighboringPlaneSet set = NeighboringPlaneSet.forPlane(plane);
            int[] neighbours = {set.plane0(), set.plane1(), set.plane2(), set.plane3()};
            for (int neighbour : neighbours) {
                assertNotEquals(plane >>> 1, neighbour >>> 1, "plane " + plane);
            }
        }
        assertSame(NeighboringPlaneSet.forPlane(4), NeighboringPlaneSet.forPlane(5));
    }

    // Checks that plane indices outside 0 to 5 are rejected
    @Test
    void planeIndicesOutsideTheFrustumAreRejected() {
        for (int index : new int[]{-1, 6, 7, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IndexOutOfBoundsException.class, () -> NeighboringPlaneSet.forPlane(index));
        }
    }

    // Checks that a point straight ahead is inside every base plane
    @Test
    void theBasePlanesAreTheViewFrustum() {
        Vector4f[] planes = new BaseClippingPlanes(PROJECTION).getPlanes();
        assertEquals(6, planes.length);
        for (Vector4f plane : planes) {
            assertTrue(plane.x * 0 + plane.y * 0 + plane.z * -10 + plane.w > 0, plane.toString());
        }
    }

    // Checks the plane order and the four dimensional unit length for the identity clip cube
    @Test
    void theBasePlanesRunRightLeftTopBottomFarNear() {
        Vector4f[] planes = new BaseClippingPlanes(new Matrix4f()).getPlanes();
        float[][] outside = {{2, 0, 0}, {-2, 0, 0}, {0, 2, 0}, {0, -2, 0}, {0, 0, 2}, {0, 0, -2}};
        for (int i = 0; i < 6; i++) {
            Vector4f plane = planes[i];
            assertEquals(1.0f, plane.length(), 1.0e-6f);
            assertTrue(plane.w > 0, "origin inside plane " + i);
            float[] point = outside[i];
            assertTrue(plane.x * point[0] + plane.y * point[1] + plane.z * point[2] + plane.w < 0, "plane " + i);
        }
        BaseClippingPlanes base = new BaseClippingPlanes(new Matrix4f());
        assertSame(base.getPlanes(), base.getPlanes());
    }

    // Checks the factory decision for every culling mode and distance combination
    @Test
    void theFactoryFollowsIrisDecisionTree() {
        assertSame(ShadowFrustums.NON_CULLING, ShadowFrustums.create(ShadowContentSettings.Culling.OFF, 64, 0, false, 128, 0));
        assertTrue(ShadowFrustums.NON_CULLING.testAab(1e6F, 1e6F, 1e6F, 2e6F, 2e6F, 2e6F));
        assertSame(ShadowFrustums.NON_CULLING, ShadowFrustums.create(ShadowContentSettings.Culling.ON, 0, 0, true, 128, 0));
        assertSame(ShadowFrustums.NON_CULLING, ShadowFrustums.create(ShadowContentSettings.Culling.ON, 256, 0, true, 128, 0));
        assertInstanceOf(ShadowBoxCuller.class, ShadowFrustums.create(ShadowContentSettings.Culling.ON, 64, 0, true, 128, 0));
        assertInstanceOf(ShadowBoxCuller.class, ShadowFrustums.create(ShadowContentSettings.Culling.ON, 128, 0, true, 128, 0));
        assertInstanceOf(SafeZoneCullingFrustum.class,
                ShadowFrustums.create(ShadowContentSettings.Culling.REVERSED, 256, 32, true, 128, 0, PROJECTION));
        AdvancedShadowCullingFrustum unbounded = (AdvancedShadowCullingFrustum)
                ShadowFrustums.create(ShadowContentSettings.Culling.ON, 256, 0, false, 128, 0, PROJECTION);
        assertNull(unbounded.boxCuller);
        AdvancedShadowCullingFrustum equal = (AdvancedShadowCullingFrustum)
                ShadowFrustums.create(ShadowContentSettings.Culling.ON, 128, 0, false, 128, 0, PROJECTION);
        assertNull(equal.boxCuller);
        AdvancedShadowCullingFrustum noDistance = (AdvancedShadowCullingFrustum)
                ShadowFrustums.create(ShadowContentSettings.Culling.ON, 0, 0, false, 128, 0, PROJECTION);
        assertNull(noDistance.boxCuller);
        AdvancedShadowCullingFrustum bounded = (AdvancedShadowCullingFrustum)
                ShadowFrustums.create(ShadowContentSettings.Culling.ON, 64, 0, false, 128, 0);
        assertNotNull(bounded.boxCuller);
        assertNotNull(Mixins.construct(ShadowFrustums.class));
    }

    // Checks the factory with a null mode and a null projection and odd distances
    @Test
    void theFactoryHandlesNullAndOddInputs() {
        assertInstanceOf(AdvancedShadowCullingFrustum.class, ShadowFrustums.create(null, 64, 0, false, 128, 0, PROJECTION));
        assertSame(ShadowFrustums.NON_CULLING, ShadowFrustums.create(ShadowContentSettings.Culling.OFF, 64, 0, false, 128, 0, null));
        assertThrows(NullPointerException.class, () -> ShadowFrustums.create(ShadowContentSettings.Culling.ON, 64, 0, false, 128, 0, null));
        assertTrue(ShadowFrustums.create(ShadowContentSettings.Culling.ON, Float.NaN, 0, true, 128, 0, null).testAab(1e9f, 1e9f, 1e9f, 1e9f, 1e9f, 1e9f));
        assertSame(ShadowFrustums.NON_CULLING, ShadowFrustums.create(ShadowContentSettings.Culling.ON, 64, 0, true, -16, 0, null));
        assertSame(ShadowFrustums.NON_CULLING, ShadowFrustums.create(ShadowContentSettings.Culling.ON, -64, 0, true, 128, 0, null));
    }

    // Checks that the light vector has unit length and falls back to straight up for a NaN sun path
    @Test
    void theLightVectorIsUnitLengthOrStraightUp() {
        Vector3f light = ShadowFrustums.shadowLightVectorFromOrigin();
        assertEquals(1.0f, light.length(), 1.0e-6f);
        CelestialUniforms.setSunPathRotation(Float.NaN);
        assertEquals(new Vector3f(0, 1, 0), ShadowFrustums.shadowLightVectorFromOrigin());
        AdvancedShadowCullingFrustum frustum = (AdvancedShadowCullingFrustum)
                ShadowFrustums.create(ShadowContentSettings.Culling.ON, 256, 0, false, 128, 0, PROJECTION);
        assertTrue(frustum.testAab(-1, -1, -11, 1, 1, -9));
    }

    // Checks that the advanced frustum keeps casters between the light and the view
    @Test
    void theAdvancedFrustumKeepsCastersBetweenTheLightAndTheView() {
        AdvancedShadowCullingFrustum frustum = new AdvancedShadowCullingFrustum(PROJECTION, new Vector3f(0, 1, 0), new ShadowBoxCuller(512));
        assertTrue(frustum.testAab(-1, -1, -11, 1, 1, -9));
        assertTrue(frustum.testAab(-1, 100, -11, 1, 102, -9));
        assertFalse(frustum.testAab(-1, -300, 290, 1, -298, 292));
        assertFalse(frustum.testAab(600, 0, 0, 601, 1, 1));
        assertEquals(AdvancedShadowCullingFrustum.INSIDE, frustum.checkCornerVisibility(-1, -1, -11, 1, 1, -9));
        assertEquals(AdvancedShadowCullingFrustum.INTERSECT, frustum.checkCornerVisibility(-1000, -1, -11, 1000, 1, -9));
        assertEquals(AdvancedShadowCullingFrustum.OUTSIDE, frustum.checkCornerVisibility(-1, -300, 290, 1, -298, 292));
        assertNotNull(new AdvancedShadowCullingFrustum(PROJECTION, new Vector3f(1, 0, 0), null));
        assertNotNull(new AdvancedShadowCullingFrustum(new Matrix4f(), new Vector3f(0, 0, 1), null));
        assertNotNull(new AdvancedShadowCullingFrustum(new Matrix4f().scale(1, 1, 0), new Vector3f(0.3F, 0.5F, 0.8F), null));
    }

    // Checks the plane count for an axis aligned light on the identity cube and for a NaN matrix
    @Test
    void edgeOnPlanesAreKeptAndNaNPlanesAreDropped() {
        AdvancedShadowCullingFrustum cube = new AdvancedShadowCullingFrustum(new Matrix4f(), new Vector3f(1, 0, 0), null);
        assertEquals(9, Mixins.<Integer>get(cube, "planeCount"));
        Matrix4f nan = new Matrix4f().m00(Float.NaN).m11(Float.NaN).m22(Float.NaN).m33(Float.NaN);
        AdvancedShadowCullingFrustum empty = new AdvancedShadowCullingFrustum(nan, new Vector3f(0, 1, 0), null);
        assertEquals(0, Mixins.<Integer>get(empty, "planeCount"));
        assertEquals(AdvancedShadowCullingFrustum.INSIDE, empty.checkCornerVisibility(1e9f, 1e9f, 1e9f, 2e9f, 2e9f, 2e9f));
    }

    // Checks that planes past the store size are dropped
    @Test
    void planesPastTheStoreSizeAreDropped() {
        AdvancedShadowCullingFrustum frustum = new AdvancedShadowCullingFrustum(PROJECTION, new Vector3f(0, 1, 0), null);
        for (int i = 0; i < 20; i++) {
            Mixins.call(frustum, "addPlane", 0.0f, 0.0f, 0.0f, 1.0f);
        }
        assertEquals(13, Mixins.<Integer>get(frustum, "planeCount"));
    }

    // Checks that the frustum classifies random boxes exactly like the step by step reference for random cameras and lights
    @Test
    void theFrustumMatchesTheReferenceForRandomInputs() {
        Random random = new Random(20261026L);
        for (int frustumIndex = 0; frustumIndex < 300; frustumIndex++) {
            Matrix4f projection = new Matrix4f().perspective(0.5f + random.nextFloat() * 2.0f, 0.5f + random.nextFloat() * 2.0f,
                    0.05f, 16.0f + random.nextFloat() * 512.0f)
                    .rotateXYZ(random.nextFloat() * 6.3f, random.nextFloat() * 6.3f, random.nextFloat() * 6.3f);
            Vector3f light = new Vector3f(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f).normalize();
            if (frustumIndex % 10 == 0) {
                light.set(0, 1, 0);
            }
            AdvancedShadowCullingFrustum frustum = new AdvancedShadowCullingFrustum(projection, light, null);
            ReferenceFrustum reference = new ReferenceFrustum(projection, light);
            assertEquals(reference.planeCount, Mixins.<Integer>get(frustum, "planeCount"));
            for (int boxIndex = 0; boxIndex < 200; boxIndex++) {
                float x = (random.nextFloat() - 0.5f) * 600.0f;
                float y = (random.nextFloat() - 0.5f) * 600.0f;
                float z = (random.nextFloat() - 0.5f) * 600.0f;
                float size = random.nextFloat() * 32.0f;
                assertEquals(reference.check(x, y, z, x + size, y + size, z + size),
                        frustum.checkCornerVisibility(x, y, z, x + size, y + size, z + size));
            }
        }
    }

    // Checks that the safe zone always draws nearby and never past the outer bound
    @Test
    void theSafeZoneAlwaysDrawsNearbyAndNeverFarAway() {
        SafeZoneCullingFrustum frustum = new SafeZoneCullingFrustum(PROJECTION, new Vector3f(0, 1, 0),
                new ShadowBoxCuller(16), new ShadowBoxCuller(256));
        assertTrue(frustum.testAab(-1, -1, 4, 1, 1, 6));
        assertFalse(frustum.testAab(300, 0, 0, 301, 1, 1));
        assertTrue(frustum.testAab(-1, -1, -101, 1, 1, -99));
        assertFalse(frustum.testAab(-1, -200, 190, 1, -198, 192));
        SafeZoneCullingFrustum noBounds = new SafeZoneCullingFrustum(PROJECTION, new Vector3f(0, 1, 0), null, null);
        assertTrue(noBounds.testAab(-1, -1, -11, 1, 1, -9));
        assertFalse(noBounds.testAab(-1, -300, 290, 1, -298, 292));
    }

    // Checks that the outer bound wins over the inner zone when the zone is the larger one
    @Test
    void theOuterBoundWinsOverALargerZone() {
        SafeZoneCullingFrustum frustum = new SafeZoneCullingFrustum(PROJECTION, new Vector3f(0, 1, 0),
                new ShadowBoxCuller(64), new ShadowBoxCuller(8));
        assertFalse(frustum.testAab(20, 0, 20, 21, 1, 21));
        assertTrue(frustum.testAab(-1, -1, 2, 1, 1, 4));
    }

    // Step by step reference of the advanced frustum with one array per plane and fresh vectors for every step
    private static final class ReferenceFrustum {
        // Plane coefficients one array per plane
        private final float[][] planes = new float[13][];
        // Light direction
        private final Vector3f light;
        // Number of planes stored
        private int planeCount;

        // Builds the reference planes
        ReferenceFrustum(Matrix4f modelViewProjection, Vector3f light) {
            this.light = light;
            Vector4f[] base = new BaseClippingPlanes(modelViewProjection).getPlanes();
            boolean[] isBack = new boolean[base.length];
            for (int i = 0; i < base.length; i++) {
                float dot = new Vector3f(base[i].x(), base[i].y(), base[i].z()).dot(light);
                isBack[i] = dot > 0.0f;
                if (dot > 0.0f || dot == 0.0f) {
                    add(new float[]{base[i].x, base[i].y, base[i].z, base[i].w});
                }
            }
            for (int i = 0; i < base.length; i++) {
                if (!isBack[i]) {
                    continue;
                }
                NeighboringPlaneSet neighbours = NeighboringPlaneSet.forPlane(i);
                for (int neighbour : new int[]{neighbours.plane0(), neighbours.plane1(), neighbours.plane2(), neighbours.plane3()}) {
                    if (!isBack[neighbour]) {
                        edge(base[i], base[neighbour]);
                    }
                }
            }
        }

        // Appends one plane until the store is full
        private void add(float[] plane) {
            if (this.planeCount < 13) {
                this.planes[this.planeCount++] = plane;
            }
        }

        // Returns the cross product of two vectors as a new vector
        private static Vector3f cross(Vector3f first, Vector3f second) {
            return new Vector3f(first.x(), first.y(), first.z()).cross(second);
        }

        // Appends the plane through the meeting line of two planes
        private void edge(Vector4f back, Vector4f front) {
            Vector3f backNormal = new Vector3f(back.x(), back.y(), back.z());
            Vector3f frontNormal = new Vector3f(front.x(), front.y(), front.z());
            Vector3f intersection = cross(backNormal, frontNormal);
            Vector3f edgeNormal = cross(intersection, this.light);
            Vector3f ixb = cross(intersection, backNormal);
            Vector3f fxi = cross(frontNormal, intersection);
            ixb.mul(-front.w());
            fxi.mul(-back.w());
            ixb.add(fxi);
            float lengthSquared = intersection.x() * intersection.x() + intersection.y() * intersection.y()
                    + intersection.z() * intersection.z();
            if (lengthSquared == 0.0f) {
                return;
            }
            Vector3f point = ixb.mul(1.0f / lengthSquared);
            add(new float[]{edgeNormal.x(), edgeNormal.y(), edgeNormal.z(), -edgeNormal.dot(point)});
        }

        // Classifies a box against the reference planes
        int check(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            boolean inside = true;
            for (int i = 0; i < this.planeCount; ++i) {
                float[] plane = this.planes[i];
                float outsideX = plane[0] < 0 ? minX : maxX;
                float outsideY = plane[1] < 0 ? minY : maxY;
                float outsideZ = plane[2] < 0 ? minZ : maxZ;
                if (plane[0] * outsideX + plane[1] * outsideY + plane[2] * outsideZ >= -plane[3]) {
                    inside &= plane[0] * (plane[0] < 0 ? maxX : minX) + plane[1] * (plane[1] < 0 ? maxY : minY)
                            + plane[2] * (plane[2] < 0 ? maxZ : minZ) + plane[3] >= 0;
                } else {
                    return AdvancedShadowCullingFrustum.OUTSIDE;
                }
            }
            return inside ? AdvancedShadowCullingFrustum.INSIDE : AdvancedShadowCullingFrustum.INTERSECT;
        }
    }
}
