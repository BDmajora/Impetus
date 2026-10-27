package com.bdmajora.impetus.umbra.pipeline.shadow;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Covers the shadow camera matrices
class ShadowCameraTest {
    // Returns the model view built step by step with the same rotations and grid snap
    private static Matrix4f reference(float shadowAngle, float sunPathRotation, float interval, Vector3d camera) {
        float skyAngle = shadowAngle < 0.25f ? shadowAngle + 0.75f : shadowAngle - 0.25f;
        Matrix4f expected = new Matrix4f()
                .rotateX((float) Math.toRadians(90.0f))
                .rotateZ((float) Math.toRadians(skyAngle * -360.0f))
                .rotateX((float) Math.toRadians(sunPathRotation));
        if (Math.abs(interval) != 0.0f) {
            float half = interval / 2.0f;
            expected.translate((float) camera.x % interval - half, (float) camera.y % interval - half,
                    (float) camera.z % interval - half);
        }
        return expected;
    }

    // Checks that both matrices stay identity until the first update
    @Test
    void bothMatricesAreIdentityBeforeTheFirstUpdate() {
        ShadowCamera camera = new ShadowCamera(160.0f, -100.05f, 156.0f, 2.0f, null, 25.0f);
        assertEquals(new Matrix4f(), camera.projection());
        assertEquals(new Matrix4f(), camera.modelView());
    }

    // Checks the orthographic projection against the JOML one
    @Test
    void withoutAFieldOfViewTheProjectionIsOrthographic() {
        ShadowCamera camera = new ShadowCamera(160.0f, -100.05f, 156.0f, 2.0f, null, 0.0f);
        camera.update(0.1f, new Vector3d());
        assertEquals(new Matrix4f().setOrtho(-160.0f, 160.0f, -160.0f, 160.0f, -100.05f, 156.0f), camera.projection());
        assertNull(camera.fov());
        assertEquals(-100.05f, camera.nearPlane());
        assertEquals(156.0f, camera.farPlane());
    }

    // Checks the perspective projection entries for a field of view
    @Test
    void aFieldOfViewGivesThePerspectiveProjection() {
        ShadowCamera camera = new ShadowCamera(160.0f, 0.05f, 256.0f, 2.0f, 90.0f, 0.0f);
        camera.update(0.1f, new Vector3d());
        Matrix4f projection = camera.projection();
        float yScale = (float) (1.0f / Math.tan(Math.toRadians(90.0f) * 0.5f));
        assertEquals(yScale, projection.m00());
        assertEquals(yScale, projection.m11());
        assertEquals((256.0f + 0.05f) / (0.05f - 256.0f), projection.m22());
        assertEquals(-1.0f, projection.m23());
        assertEquals(2.0f * 256.0f * 0.05f / (0.05f - 256.0f), projection.m32());
        assertEquals(1.0f, projection.m33());
        assertEquals(0.0f, projection.m01());
        assertEquals(90.0f, camera.fov());
        assertEquals(ShadowCamera.perspective(90.0f, 0.05f, 256.0f), projection);
    }

    // Checks that each update restores the projection even after it was changed through the live matrix
    @Test
    void eachUpdateRestoresTheProjection() {
        ShadowCamera camera = new ShadowCamera(64.0f, -10.0f, 10.0f, 2.0f, null, 0.0f);
        camera.update(0.0f, new Vector3d());
        Matrix4f first = new Matrix4f(camera.projection());
        camera.projection().scale(3.0f);
        camera.update(0.3f, new Vector3d(5, 5, 5));
        assertEquals(first, camera.projection());
        assertSame(camera.projection(), camera.projection());
    }

    // Checks the sky angle on both sides of the quarter mark and for NaN
    @Test
    void theSkyAngleWrapsAtTheQuarterMark() {
        assertEquals(0.75f, ShadowCamera.skyAngle(0.0f));
        assertEquals(0.24f + 0.75f, ShadowCamera.skyAngle(0.24f));
        assertEquals(0.0f, ShadowCamera.skyAngle(0.25f));
        assertEquals(0.25f, ShadowCamera.skyAngle(0.5f));
        assertEquals(-1.0f + 0.75f, ShadowCamera.skyAngle(-1.0f));
        assertTrue(Float.isNaN(ShadowCamera.skyAngle(Float.NaN)));
        assertEquals(Float.POSITIVE_INFINITY, ShadowCamera.skyAngle(Float.POSITIVE_INFINITY));
    }

    // Checks the snap offset for positive and negative and large and non finite coordinates
    @Test
    void theSnapOffsetCentresOnTheGridCell() {
        assertEquals(0.0f, ShadowCamera.snapOffset(5.0, 2.0f));
        assertEquals(-2.0f, ShadowCamera.snapOffset(-5.0, 2.0f));
        assertEquals(-0.5f, ShadowCamera.snapOffset(0.5, 2.0f));
        assertEquals(-1.0f, ShadowCamera.snapOffset(0.0, 2.0f));
        assertEquals(-1.0f, ShadowCamera.snapOffset(1.0e9 + 0.3, 2.0f));
        assertTrue(Float.isNaN(ShadowCamera.snapOffset(Double.NaN, 2.0f)));
        assertTrue(Float.isNaN(ShadowCamera.snapOffset(Double.MAX_VALUE, 2.0f)));
        assertTrue(Float.isNaN(ShadowCamera.snapOffset(4.0, Float.NaN)));
        assertEquals(0.5f - 1.5f, ShadowCamera.snapOffset(3.5, 3.0f));
        assertEquals(-1.0f + 1.0f, ShadowCamera.snapOffset(-1.0, -2.0f));
    }

    // Checks the model view against the step by step reference for several angles and positions
    @Test
    void theModelViewMatchesTheReferenceRotationAndSnap() {
        double[][] positions = {{0, 64, 0}, {100.5, 70.25, -33.75}, {-1.0e6, 255.0, 1.0e6}, {1.0e9, 0.0, -1.0e9}};
        float[] angles = {0.0f, 0.1f, 0.2499f, 0.25f, 0.4f, 0.5f};
        for (double[] position : positions) {
            Vector3d at = new Vector3d(position[0], position[1], position[2]);
            for (float angle : angles) {
                ShadowCamera camera = new ShadowCamera(160.0f, -100.05f, 156.0f, 2.0f, null, 25.0f);
                camera.update(angle, at);
                assertEquals(reference(angle, 25.0f, 2.0f, at), camera.modelView(), angle + " at " + at);
            }
        }
    }

    // Checks that a zero interval of either sign skips the snap and a NaN interval spreads NaN into the translation
    @Test
    void aZeroIntervalSkipsTheSnap() {
        Vector3d at = new Vector3d(10.3, 20.6, 30.9);
        for (float interval : new float[]{0.0f, -0.0f}) {
            ShadowCamera camera = new ShadowCamera(16.0f, -1.0f, 1.0f, interval, null, 0.0f);
            camera.update(0.1f, at);
            assertEquals(reference(0.1f, 0.0f, 0.0f, at), camera.modelView());
        }
        ShadowCamera nan = new ShadowCamera(16.0f, -1.0f, 1.0f, Float.NaN, null, 0.0f);
        nan.update(0.1f, at);
        assertTrue(Float.isNaN(nan.modelView().m30()));
    }

    // Checks that repeated updates at the same input give the same model view
    @Test
    void repeatedUpdatesAreStable() {
        ShadowCamera camera = new ShadowCamera(16.0f, -1.0f, 1.0f, 2.0f, null, 10.0f);
        Vector3d at = new Vector3d(7.7, 8.8, 9.9);
        camera.update(0.3f, at);
        Matrix4f first = new Matrix4f(camera.modelView());
        camera.modelView().zero();
        camera.update(0.3f, at);
        assertEquals(first, camera.modelView());
    }

    // Checks that a null camera position is rejected
    @Test
    void aNullCameraIsRejected() {
        ShadowCamera camera = new ShadowCamera(16.0f, -1.0f, 1.0f, 2.0f, null, 0.0f);
        assertThrows(NullPointerException.class, () -> camera.update(0.1f, null));
    }
}
