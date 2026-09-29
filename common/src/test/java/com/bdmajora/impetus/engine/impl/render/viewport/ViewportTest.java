package com.bdmajora.impetus.engine.impl.render.viewport;

import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;
import com.bdmajora.impetus.engine.impl.render.viewport.frustum.SimpleFrustum;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ViewportTest {
    @Test
    void cameraTransformSplitsIntegerAndFraction() {
        CameraTransform t = new CameraTransform(1000.75, -3.25, 0.5);
        assertEquals(1000, t.intX);
        assertEquals(0.75f, t.fracX, 1e-6);
        assertEquals(-3, t.intY);
        assertEquals(-0.25f, t.fracY, 1e-6);
        assertEquals(0.5f, t.fracZ, 1e-6);
        assertEquals(t, new CameraTransform(1000.75, -3.25, 0.5));
        assertNotEquals(t, new CameraTransform(0, 0, 0));
        assertNotEquals(t, null);
        assertNotEquals(t, "x");
        assertEquals(t.hashCode(), new CameraTransform(1000.75, -3.25, 0.5).hashCode());
    }

    @Test
    void viewportTestsBoxesRelativeToTheCamera() {
        Frustum everything = (a, b, c, d, e, f) -> true;
        Viewport viewport = new Viewport(everything, new Vector3d(17.5, -1.5, 33));
        assertEquals(1, viewport.getChunkCoord().x());
        assertEquals(-1, viewport.getChunkCoord().y());
        assertEquals(2, viewport.getChunkCoord().z());
        assertEquals(17, viewport.getBlockCoord().x());
        assertEquals(-2, viewport.getBlockCoord().y());
        assertEquals(17, viewport.getTransform().intX);
        assertTrue(viewport.isBoxVisible(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
        assertTrue(viewport.isBoxVisible(0, 0, 0, 8f));
        FrustumIntersection intersection = new FrustumIntersection(new Matrix4f().perspective(1f, 1f, 0.1f, 100f));
        Viewport real = new Viewport(new SimpleFrustum(intersection), new Vector3d(0, 0, 0));
        assertTrue(real.isBoxVisible(0, 0, -10, 1f, 1f, 1f));
        assertFalse(real.isBoxVisible(0, 0, 10, 1f, 1f, 1f));
        ViewportProvider provider = () -> real;
        assertSame(real, provider.impetus$createViewport());
    }
}
