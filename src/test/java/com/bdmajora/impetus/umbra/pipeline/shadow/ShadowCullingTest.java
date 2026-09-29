package com.bdmajora.impetus.umbra.pipeline.shadow;

import com.bdmajora.impetus.umbra.pipeline.ShadowContentSettings;
import com.bdmajora.testing.Mc;
import com.bdmajora.testing.Mixins;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShadowCullingTest {
    // A camera at the origin looking down -Z with a 90 degree field of view out to 256 blocks
    private static final Matrix4f PROJECTION = new Matrix4f().perspective((float) Math.toRadians(90), 1.0F, 0.05F, 256.0F);

    @BeforeEach
    void noWorld() {
        Mc.client();
    }

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

    @Test
    void neighbouringPlanesShareAnAxis() {
        for (int plane = 0; plane < 6; plane++) {
            NeighboringPlaneSet set = NeighboringPlaneSet.forPlane(plane);
            int[] neighbours = {set.plane0(), set.plane1(), set.plane2(), set.plane3()};
            for (int neighbour : neighbours) {
                // Never itself or its opposite
                assertNotEquals(plane >>> 1, neighbour >>> 1, "plane " + plane);
            }
        }
        assertSame(NeighboringPlaneSet.forPlane(4), NeighboringPlaneSet.forPlane(5));
    }

    @Test
    void theBasePlanesAreTheViewFrustum() {
        Vector4f[] planes = new BaseClippingPlanes(PROJECTION).getPlanes();
        assertEquals(6, planes.length);
        // A point straight ahead is on the inside of every plane
        for (Vector4f plane : planes) {
            assertTrue(plane.x * 0 + plane.y * 0 + plane.z * -10 + plane.w > 0, plane.toString());
        }
    }

    @Test
    void theFactoryFollowsIrisDecisionTree() {
        assertSame(ShadowFrustums.NON_CULLING, ShadowFrustums.create(ShadowContentSettings.Culling.OFF, 64, 0, false, 128, 0));
        assertTrue(ShadowFrustums.NON_CULLING.testAab(1e6F, 1e6F, 1e6F, 2e6F, 2e6F, 2e6F));
        // A voxelising pack with no stated mode gets distance-only culling, or none when it covers the render distance
        assertSame(ShadowFrustums.NON_CULLING, ShadowFrustums.create(ShadowContentSettings.Culling.ON, 0, 0, true, 128, 0));
        assertSame(ShadowFrustums.NON_CULLING, ShadowFrustums.create(ShadowContentSettings.Culling.ON, 256, 0, true, 128, 0));
        assertInstanceOf(ShadowBoxCuller.class, ShadowFrustums.create(ShadowContentSettings.Culling.ON, 64, 0, true, 128, 0));
        assertInstanceOf(SafeZoneCullingFrustum.class,
                ShadowFrustums.create(ShadowContentSettings.Culling.REVERSED, 256, 32, true, 128, 0, PROJECTION));
        AdvancedShadowCullingFrustum unbounded = (AdvancedShadowCullingFrustum)
                ShadowFrustums.create(ShadowContentSettings.Culling.ON, 256, 0, false, 128, 0, PROJECTION);
        assertNull(unbounded.boxCuller);
        AdvancedShadowCullingFrustum noDistance = (AdvancedShadowCullingFrustum)
                ShadowFrustums.create(ShadowContentSettings.Culling.ON, 0, 0, false, 128, 0, PROJECTION);
        assertNull(noDistance.boxCuller);
        AdvancedShadowCullingFrustum bounded = (AdvancedShadowCullingFrustum)
                ShadowFrustums.create(ShadowContentSettings.Culling.ON, 64, 0, false, 128, 0);
        assertNotNull(bounded.boxCuller);
        assertNotNull(Mixins.construct(ShadowFrustums.class));
    }

    @Test
    void theAdvancedFrustumKeepsCastersBetweenTheLightAndTheView() {
        // Light straight above: the camera frustum's sides are extruded upward, so a caster high above the view still casts
        AdvancedShadowCullingFrustum frustum = new AdvancedShadowCullingFrustum(PROJECTION, new Vector3f(0, 1, 0), new ShadowBoxCuller(512));
        assertTrue(frustum.testAab(-1, -1, -11, 1, 1, -9));
        assertTrue(frustum.testAab(-1, 100, -11, 1, 102, -9));
        // Far behind the camera and below it, nothing the view sees can be shaded by it
        assertFalse(frustum.testAab(-1, -300, 290, 1, -298, 292));
        // Outside the distance cube is rejected before the planes are consulted
        assertFalse(frustum.testAab(600, 0, 0, 601, 1, 1));
        // The three-way answer distinguishes fully inside from straddling
        assertEquals(AdvancedShadowCullingFrustum.INSIDE, frustum.checkCornerVisibility(-1, -1, -11, 1, 1, -9));
        assertEquals(AdvancedShadowCullingFrustum.INTERSECT, frustum.checkCornerVisibility(-1000, -1, -11, 1000, 1, -9));
        assertEquals(AdvancedShadowCullingFrustum.OUTSIDE, frustum.checkCornerVisibility(-1, -300, 290, 1, -298, 292));
        // A light along a plane's normal and edge-on light directions exercise the edge and parallel cases
        assertNotNull(new AdvancedShadowCullingFrustum(PROJECTION, new Vector3f(1, 0, 0), null));
        assertNotNull(new AdvancedShadowCullingFrustum(new Matrix4f(), new Vector3f(0, 0, 1), null));
        assertNotNull(new AdvancedShadowCullingFrustum(new Matrix4f().scale(1, 1, 0), new Vector3f(0.3F, 0.5F, 0.8F), null));
    }

    @Test
    void theSafeZoneAlwaysDrawsNearbyAndNeverFarAway() {
        SafeZoneCullingFrustum frustum = new SafeZoneCullingFrustum(PROJECTION, new Vector3f(0, 1, 0),
                new ShadowBoxCuller(16), new ShadowBoxCuller(256));
        // Behind the camera but inside the voxel zone
        assertTrue(frustum.testAab(-1, -1, 4, 1, 1, 6));
        assertFalse(frustum.testAab(300, 0, 0, 301, 1, 1));
        assertTrue(frustum.testAab(-1, -1, -101, 1, 1, -99));
        assertFalse(frustum.testAab(-1, -200, 190, 1, -198, 192));
        SafeZoneCullingFrustum noBounds = new SafeZoneCullingFrustum(PROJECTION, new Vector3f(0, 1, 0), null, null);
        assertTrue(noBounds.testAab(-1, -1, -11, 1, 1, -9));
    }
}
