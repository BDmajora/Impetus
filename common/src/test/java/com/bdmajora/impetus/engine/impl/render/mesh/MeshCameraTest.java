package com.bdmajora.impetus.engine.impl.render.mesh;

import com.bdmajora.impetus.engine.impl.render.chunk.ChunkRenderMatrices;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MeshCameraTest {
    @Test
    void theEyeIsWhereTheModelViewPutsTheViewOrigin() {
        // Vanilla's eye-height translation: the view origin sits 1.62 above the render entity's feet
        ChunkRenderMatrices matrices = new ChunkRenderMatrices(new Matrix4f(), new Matrix4f().translate(0f, -1.62f, 0f));
        MeshCamera camera = MeshCamera.of(matrices, 8.0, 63.5, -0.5);
        assertEquals(63.5, camera.renderY());
        assertEquals(65.12, camera.eyeY(), 1.0e-4);
        // The feet sit in section 3, the eye in section 4, which is the one culling has to use
        assertEquals(4, camera.sectionY());
        assertEquals(0, camera.sectionX());
        assertEquals(-1, camera.sectionZ());
    }

    @Test
    void aSingularModelViewLeavesTheEyeOnTheRenderOrigin() {
        MeshCamera camera = MeshCamera.of(new ChunkRenderMatrices(new Matrix4f(), new Matrix4f().zero()), 1.0, 2.0, 3.0);
        assertEquals(MeshCamera.at(1.0, 2.0, 3.0), camera);
        assertEquals(1.0, camera.eyeX());
        assertEquals(3.0, camera.eyeZ());
    }
}
