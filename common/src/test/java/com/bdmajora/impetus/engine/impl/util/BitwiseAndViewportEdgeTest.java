package com.bdmajora.impetus.engine.impl.util;

import com.bdmajora.impetus.engine.impl.render.shader.ShaderLoader;
import com.bdmajora.impetus.engine.impl.render.viewport.Viewport;
import com.bdmajora.impetus.engine.impl.render.viewport.frustum.Frustum;
import com.bdmajora.impetus.engine.impl.render.viewport.frustum.SimpleFrustum;
import com.bdmajora.impetus.engine.impl.texture.MipmapHelper;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

// Tests the comparison helpers and the viewport and loader argument checks.
class BitwiseAndViewportEdgeTest {
    // Compares correctly at the int limits where subtraction overflows.
    @Test
    void comparisonsAtLimits() {
        int[] values = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1, 0, 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
        for (int a : values) {
            for (int b : values) {
                assertEquals(a < b ? 1 : 0, BitwiseMath.lessThan(a, b), a + " < " + b);
                assertEquals(a > b ? 1 : 0, BitwiseMath.greaterThan(a, b), a + " > " + b);
            }
        }
    }

    // Rejects null parts of a viewport and tests boxes far from the camera.
    @Test
    void viewport() {
        Frustum frustum = Mockito.mock(Frustum.class);
        Mockito.when(frustum.testAab(Mockito.anyFloat(), Mockito.anyFloat(), Mockito.anyFloat(), Mockito.anyFloat(), Mockito.anyFloat(), Mockito.anyFloat())).thenReturn(true);
        assertThrows(NullPointerException.class, () -> new Viewport(null, new Vector3d()));
        assertThrows(NullPointerException.class, () -> new Viewport(frustum, null));
        Viewport viewport = new Viewport(frustum, new Vector3d(-0.5, 70.25, 1_000_000.75));
        assertEquals(-1, viewport.getChunkCoord().x());
        assertEquals(4, viewport.getChunkCoord().y());
        assertEquals(-1, viewport.getBlockCoord().x());
        assertTrue(viewport.isBoxVisible(Integer.MAX_VALUE, Integer.MIN_VALUE, 0, 8f));
        assertTrue(viewport.isBoxVisible(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
        assertNull(viewport.getVpMatrix());
        assertSame(frustum, viewport.getFrustum());
        assertThrows(NullPointerException.class, () -> new SimpleFrustum(null));
    }

    // Rejects null shader arguments.
    @Test
    void shaderLoaderNulls() {
        assertThrows(NullPointerException.class, () -> ShaderLoader.getShaderSource(null));
        assertThrows(NullPointerException.class, () -> ShaderLoader.loadShader(null, "a", null));
        assertThrows(RuntimeException.class, () -> ShaderLoader.getShaderSource("impetus:does/not/exist.glsl"));
    }

    // Averages equal pixels to themselves and ignores transparent pixels.
    @Test
    void mipmapAverages() {
        assertEquals(0xFF336699, MipmapHelper.weightedAverageColor(0xFF336699, 0xFF336699));
        assertEquals(0, MipmapHelper.weightedAverageColor(0, 0));
        int fromTransparent = MipmapHelper.weightedAverageColor(0x00000000, 0xFF336699);
        assertEquals(0x336699, fromTransparent & 0xFFFFFF);
        assertEquals(0x3F, fromTransparent >>> 24);
        assertEquals(0x336699, MipmapHelper.weightedAverageColor(0xFF336699, 0x00FFFFFF) & 0xFFFFFF);
        int mixed = MipmapHelper.weightedAverageColor(0x80FF0000, 0xFF00FF00);
        assertEquals((0x80 + 0xFF) >> 1, mixed >>> 24);
    }
}
